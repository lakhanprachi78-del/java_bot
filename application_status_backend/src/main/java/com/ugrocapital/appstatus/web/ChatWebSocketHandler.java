package com.ugrocapital.appstatus.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ugrocapital.appstatus.auth.AuthContext;
import com.ugrocapital.appstatus.auth.AuthService;
import com.ugrocapital.appstatus.auth.UnknownSessionException;
import com.ugrocapital.appstatus.dto.web.ChatResponse;
import com.ugrocapital.appstatus.service.ChatOrchestrationService;
import com.ugrocapital.appstatus.service.FeedbackLogService;
import com.ugrocapital.appstatus.service.QueryLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Implements the exact wire protocol chat.service.ts speaks: every
 * incoming frame is {"type": "...", "payload": {...}}, and every
 * response is the SAME shape, echoing the same "type" back (the
 * frontend keys its pending-request map on `${source}:${type}`, so the
 * type in the response MUST match the type of the request it answers).
 * A response payload containing an "error" key is treated by the
 * frontend as a rejected promise — see handleIncomingMessage() in
 * chat.service.ts.
 *
 * Supported types (from chat.models.ts's MessageType): "chat",
 * "chat.direct", "greet", "chat.reset", "feedback", "feedback.detail",
 * "health". Each case below delegates to the same service classes the
 * REST controllers use (ChatController) — this handler is purely a
 * transport adapter, it contains no business logic of its own.
 */
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ChatWebSocketHandler.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final AuthService authService;
    private final ChatOrchestrationService chatOrchestrationService;
    private final QueryLogService queryLogService;
    private final FeedbackLogService feedbackLogService;

    public ChatWebSocketHandler(
            AuthService authService,
            ChatOrchestrationService chatOrchestrationService,
            QueryLogService queryLogService,
            FeedbackLogService feedbackLogService
    ) {
        this.authService = authService;
        this.chatOrchestrationService = chatOrchestrationService;
        this.queryLogService = queryLogService;
        this.feedbackLogService = feedbackLogService;
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String type = null;
        Map<String, Object> payload;
        try {
            Map<String, Object> envelope = objectMapper.readValue(message.getPayload(), Map.class);
            type = (String) envelope.get("type");
            //noinspection unchecked
            Map<String, Object> requestPayload = (Map<String, Object>) envelope.getOrDefault("payload", Map.of());
            payload = dispatch(type, requestPayload);
        } catch (Exception e) {
            log.error("WS ERROR handling type='{}': {}", type, e.getMessage(), e);
            payload = Map.of("error", "Something went wrong processing that request. Please try again.");
        }
        sendEnvelope(session, type, payload);
    }

    private Map<String, Object> dispatch(String type, Map<String, Object> payload) {
        if (type == null) {
            return Map.of("error", "Missing message type.");
        }
        switch (type) {
            case "greet":
                return handleGreet(payload);
            case "chat":
                return handleChat(payload);
            case "chat.direct":
                return handleChatDirect(payload);
            case "chat.reset":
                return handleChatReset(payload);
            case "feedback":
                return handleFeedback(payload);
            case "feedback.detail":
                return handleFeedbackDetail(payload);
            case "health":
                return Map.of("status", "ok");
            default:
                return Map.of("error", "Unknown message type: " + type);
        }
    }

    private Map<String, Object> handleGreet(Map<String, Object> payload) {
        String sessionId = str(payload.get("session_id"));
        try {
            authService.getAuthContext(sessionId); // validates the session exists
        } catch (UnknownSessionException e) {
            return Map.of("error", "Invalid or expired session.");
        }
        return Map.of("greeting", authService.buildGreeting(sessionId));
    }

    private Map<String, Object> handleChat(Map<String, Object> payload) {
        String sessionId = str(payload.get("session_id"));
        String userMessage = str(payload.get("message"));
        boolean isHandoff = Boolean.TRUE.equals(payload.get("is_handoff"));

        try {
            ChatResponse resp = chatOrchestrationService.chat(sessionId, userMessage, isHandoff);
            return chatResponseToPayload(resp);
        } catch (ChatOrchestrationService.UnauthorizedException e) {
            return Map.of("error", "Invalid or expired session.");
        } catch (ChatOrchestrationService.BadRequestException e) {
            return Map.of("error", e.getMessage());
        } catch (RuntimeException e) {
            return Map.of("error", e.getMessage());
        }
    }

    private Map<String, Object> handleChatDirect(Map<String, Object> payload) {
        String sessionId = str(payload.get("session_id"));
        String field = str(payload.get("field"));
        String value = str(payload.get("value"));
        int offset = payload.get("offset") != null ? ((Number) payload.get("offset")).intValue() : 0;

        try {
            ChatResponse resp = chatOrchestrationService.chatDirect(sessionId, field, value, offset);
            return chatResponseToPayload(resp);
        } catch (ChatOrchestrationService.UnauthorizedException e) {
            return Map.of("error", "Invalid or expired session.");
        } catch (RuntimeException e) {
            return Map.of("error", e.getMessage());
        }
    }

    private Map<String, Object> handleChatReset(Map<String, Object> payload) {
        String sessionId = str(payload.get("session_id"));
        chatOrchestrationService.resetSession(sessionId);
        return Map.of("status", "reset");
    }

    private Map<String, Object> handleFeedback(Map<String, Object> payload) {
        long queryId = ((Number) payload.get("query_id")).longValue();
        boolean positive = Boolean.TRUE.equals(payload.get("positive"));
        Long resolvedId = queryLogService.addFeedback(queryId, positive);
        return Map.of("success", resolvedId != null);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> handleFeedbackDetail(Map<String, Object> payload) {
        Long queryId = payload.get("query_id") != null ? ((Number) payload.get("query_id")).longValue() : null;
        String queryText = (String) payload.get("query_text");
        String answerText = (String) payload.get("answer_text");
        boolean positive = Boolean.TRUE.equals(payload.get("positive"));
        List<String> tags = payload.get("tags") != null ? (List<String>) payload.get("tags") : List.of();
        String comment = (String) payload.get("comment");

        List<String> cleanTags = tags.stream().map(String::strip).filter(t -> !t.isEmpty()).toList();
        String cleanComment = comment != null ? comment.strip() : "";
        if (cleanTags.isEmpty() && cleanComment.isEmpty()) {
            return Map.of("error", "Provide at least one reason tag or a comment.");
        }

        Long newId = feedbackLogService.addFeedbackDetail(
                queryId, queryText, answerText, positive, cleanTags, cleanComment.isEmpty() ? null : cleanComment);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", newId != null);
        out.put("id", newId);
        return out;
    }

    private Map<String, Object> chatResponseToPayload(ChatResponse resp) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("reply", resp.reply);
        out.put("session_id", resp.sessionId);
        out.put("has_more", resp.hasMore);
        out.put("query_id", resp.queryId);
        return out;
    }

    private void sendEnvelope(WebSocketSession session, String type, Map<String, Object> payload) {
        try {
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("type", type);
            envelope.put("payload", payload);
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(envelope)));
            }
        } catch (Exception e) {
            log.error("WS SEND ERROR: {}", e.getMessage(), e);
        }
    }

    private String str(Object o) {
        return o == null ? "" : o.toString();
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("WS connected: {}", session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.info("WS closed: {} ({})", session.getId(), status);
    }
}
