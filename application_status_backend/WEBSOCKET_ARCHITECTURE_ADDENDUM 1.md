# Addendum — WebSocket Transport (replaces REST for all endpoints)

Supersedes **§8 (HTTP contract)** of `JAVA_MIGRATION_ARCHITECTURE.md` and **Phase 4** of
`JAVA_MIGRATION_BUILD_STEPS.md`. Everything else in both documents — auth, repository,
tools, chat engine, formatter, logs — is **unchanged**; only the transport/controller
layer changes. Nothing about the WebSocket switch weakens the security model: the
`AuthContext`-per-call discipline in the repository/tool layer applies identically
whether the call was triggered by an HTTP request or a WebSocket message.

---

## ✅ Transport confirmed from the checked-in Angular source

The checked-in Angular source uses the browser's native `WebSocket` API, not STOMP or
SockJS. It sends JSON envelopes shaped like `{"type":"chat","payload":{...}}` and
matches responses by `payload.type`. Therefore the raw WebSocket handler described at
the end of this document is the required implementation for this repository. The
STOMP configuration below is retained only as an alternative for a future frontend
rewrite and must not be used with the current Angular client.

---

## 1. New dependency

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-websocket</artifactId>
</dependency>
```
This pulls in `spring-messaging` + `spring-websocket`, which includes **server-side
SockJS support** (fallback transports, no extra library needed) and the STOMP broker
relay/simple-broker machinery.

`spring-boot-starter-web` stays — `/health` remains a plain REST endpoint (a load
balancer health check has no business going over a WebSocket).

---

## 2. Endpoint + broker configuration (STOMP alternative only)

```java
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")       // same "wide open for now" stance as CorsConfig — tighten later, same flag as §8's CORS note
                .withSockJS();                        // <-- this is what makes it SockJS-compatible on the wire
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/queue", "/topic"); // in-memory broker; swap for a real broker (RabbitMQ STOMP relay) only if you scale to multiple instances — same caveat as the in-memory conversation map in §6 of the architecture doc
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }
}
```
The current Angular client connects directly to `ws://<host>/ws` and sends a JSON
envelope. It includes `session_id` in each payload, so the Java handler must resolve
that value for every message and pass the resulting `AuthContext` to downstream
services. Do not use `Principal` or `convertAndSendToUser()` for this client.

Angular side connects to `new SockJS('http://<host>/ws')`, wraps it with STOMP, same
as it presumably does today (confirm the path — `/ws` is my assumption, adjust to match
whatever the Angular service actually dials).

---

## 3. Identifying the caller — the auth bridge (STOMP alternative only)

The existing Python auth model isn't "real" auth — it's a hardcoded `session_id →
{username, role}` map (§4.10 of the architecture doc), and that **stays exactly as
designed**. The only new problem WebSocket introduces: HTTP had `session_id` as a
request field/query param on every call; STOMP needs a way to know *which connected
socket* a reply belongs to, so it can route the response back to only that client.

**Recommended approach — resolve identity once, at CONNECT time, not per-message:**

```java
@Component
public class SessionHandshakeInterceptor implements ChannelInterceptor {

    private final AuthService authService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String sessionId = accessor.getFirstNativeHeader("session-id"); // Angular sets this as a STOMP CONNECT header
            AuthContext auth = authService.resolve(sessionId); // throws UnknownSessionException on a bad/missing id — connection gets rejected
            accessor.setUser(() -> sessionId); // Principal.getName() = session_id, this is what convertAndSendToUser() matches against
        }
        return message;
    }
}
```
Register it on the **inbound** channel in `WebSocketConfig`
(`configureClientInboundChannel`). This means:
- Angular sends the STOMP `CONNECT` frame with a native header `session-id: session-prachi` (its equivalent of what used to be a request field/query param).
- Every subsequent `SEND` frame on that same socket is already tied to a resolved `AuthContext` — **no need to re-pass `session_id` on every message body**, which is actually a nicer contract than the REST version had.
- An unknown session gets rejected at CONNECT (socket never opens) instead of failing per-request — confirm this UX is acceptable, or tell me and I'll make it fail-soft per-message instead (closer to the old 401-per-request behavior).

If your actual Angular code instead puts `session_id` inside every message payload
(rather than a CONNECT header) — also a valid pattern — say so once the frontend is
readable and I'll switch `ChatController`'s methods to resolve `AuthContext` per-message
from the payload instead of once at CONNECT. Either way the downstream services
(`ChatEngineService`, repository, etc.) don't care — they just take an `AuthContext`.

---

## 4. Destination mapping (STOMP alternative only)

| Old REST route | New STOMP `@MessageMapping` (client→server, prefixed `/app`) | Server reply destination (server→client) |
|---|---|---|
| `POST /chat` | `/app/chat.send` `{message, isHandoff}` | `/user/queue/chat.reply` |
| `POST /chat/direct` | `/app/chat.direct` `{field, value, offset}` | `/user/queue/chat.reply` (same reply channel/shape as chat.send — frontend already has to render both the same way) |
| `POST /chat/reset` | `/app/chat.reset` `{}` | `/user/queue/chat.status` `{status:"reset"}` |
| `GET /whoami` | `/app/whoami` `{}` | `/user/queue/whoami` `{username, role, displayName}` |
| `GET /greet` | `/app/greet` `{}` | `/user/queue/greet` `{greeting}` |
| `POST /api/feedback` | `/app/feedback` `{queryId, positive}` | `/user/queue/feedback` `{success}` |
| `POST /api/feedback/detail` | `/app/feedback.detail` `{...}` | `/user/queue/feedback` `{success, id}` |
| `GET /health` | *(stays REST — see §1)* | — |

```java
@Controller
public class ChatController {

    @MessageMapping("/chat.send")
    public void chat(@Payload @Valid ChatMessageRequest req, Principal principal) {
        AuthContext auth = authService.resolve(principal.getName());
        ChatResponse reply = chatEngineService.runChatTurn(auth, req.message(), req.isHandoff());
        messagingTemplate.convertAndSendToUser(principal.getName(), "/queue/chat.reply", reply);
    }
    // ... chat.direct, chat.reset, whoami, greet, feedback, feedback.detail follow the same shape
}
```

## 5. Error handling — the part REST gave you for free

HTTP status codes (400/401/500) don't exist on a STOMP frame — you need an explicit
error envelope, and a `@MessageExceptionHandler` to catch what used to be caught by
`GlobalExceptionHandler`'s `@ControllerAdvice`:

```java
public record WsErrorResponse(String code, String message) {}
// codes mirroring the old status semantics: "BLANK_MESSAGE" (was 400), "UNKNOWN_SESSION" (was 401), "INTERNAL_ERROR" (was 500, generic message only — never leak internals, same rule as §4.11)

@Controller
public class WsExceptionHandler {
    @MessageExceptionHandler
    @SendToUser("/queue/errors")
    public WsErrorResponse handle(Exception ex, Principal principal) {
        if (ex instanceof UnknownSessionException) return new WsErrorResponse("UNKNOWN_SESSION", "Session not recognized.");
        if (ex instanceof IllegalArgumentException) return new WsErrorResponse("BLANK_MESSAGE", ex.getMessage());
        log.error("WS handler error", ex); // full detail server-side only
        return new WsErrorResponse("INTERNAL_ERROR", "Something went wrong. Please try again.");
    }
}
```
Angular subscribes to `/user/queue/errors` once, globally, same as it presumably has
one shared HTTP-error interceptor today.

## 6. What does *not* change

- `ChatEngineService`, `DirectQueryService`, `ToolDispatcher`, `ApplicationQueryRepository`, `ReplyFormatter`, `QueryLogService`, `FeedbackLogService`, `LlmClient`, `AuthService` — **zero changes**. They all take an `AuthContext` + typed args and return typed results; they have no idea whether they were invoked from an `@RestController` or a `@MessageMapping`. This is exactly why the layering in the original architecture doc pays off here.
- The in-memory `CONVERSATIONS` map caveat (§6) applies identically — one WS connection per browser tab roughly maps to one "session" the same way HTTP requests did, same multi-instance/restart caveat, same fix (Redis) if/when you scale out.
- `/health` stays plain REST for infra health checks.

## 7. Updated build-steps (replaces Phase 4, Steps 12–13)

**Step 12′ — WS message DTOs 🟢**
Same records as before (§5 of the architecture doc), plus `WsErrorResponse`. Ask: *"Generate step 12: the WebSocket message DTOs."*

**Step 13′ — WebSocketConfig + SessionHandshakeInterceptor 🔴**
The CONNECT-time auth resolution is the security-sensitive piece here — equivalent risk level to the old 401 check, just relocated. Ask: *"Generate step 13a: WebSocketConfig and SessionHandshakeInterceptor."*

**Step 13″ — STOMP controllers + WsExceptionHandler 🟡**
`ChatController`, `SessionController`, `FeedbackController` as `@Controller` + `@MessageMapping`, plus the exception handler. Ask: *"Generate step 13b: the STOMP controllers and WsExceptionHandler."*

**Step 15′ — Contract test, WS edition**
Same idea as the original Step 15, but the harness needs a STOMP test client (e.g. `spring-boot-starter-websocket`'s `WebSocketStompClient` in a test) instead of Postman, since you're diffing frame payloads rather than HTTP responses.

---

## Raw SockJS fallback (only if there's no STOMP layer)

If it turns out Angular talks *raw* SockJS with its own JSON envelope (no STOMP), swap
§2–§4 for a plain `WebSocketHandler`:

```java
@Configuration
@EnableWebSocket
public class RawSockJsConfig implements WebSocketConfigurer {
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(chatWebSocketHandler(), "/ws")
                .setAllowedOriginPatterns("*")
                ;
 }
    }
}
```
with a single `TextWebSocketHandler` that parses an envelope like `{"type": "chat.send", "payload": {...}}` in `handleTextMessage`, dispatches to the same services, and writes a similarly-shaped JSON response back on that one session — you lose STOMP's automatic per-destination routing and per-user addressing, so you'd hand-roll both (a `Map<WebSocketSession, AuthContext>` instead of the Principal-based approach in §3). Everything downstream of "which `AuthContext` is this for" stays identical either way. Tell me which one it is once you can confirm, and I'll generate the matching version.
