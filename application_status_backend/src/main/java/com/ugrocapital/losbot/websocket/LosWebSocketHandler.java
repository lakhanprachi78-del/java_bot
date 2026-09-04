package com.ugrocapital.losbot.websocket;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ugrocapital.losbot.auth.AuthContext;
import com.ugrocapital.losbot.auth.AuthService;
import com.ugrocapital.losbot.auth.GreetingService;
import com.ugrocapital.losbot.dto.ChatRequest;
import com.ugrocapital.losbot.dto.ChatResponse;
import com.ugrocapital.losbot.dto.DirectChatRequest;
import com.ugrocapital.losbot.dto.DirectChatResponse;
import com.ugrocapital.losbot.dto.FeedbackDetailRequest;
import com.ugrocapital.losbot.dto.FeedbackDetailResponse;
import com.ugrocapital.losbot.dto.FeedbackRequest;
import com.ugrocapital.losbot.dto.FeedbackResponse;
import com.ugrocapital.losbot.dto.GreetingResponse;
import com.ugrocapital.losbot.dto.ResetResponse;
import com.ugrocapital.losbot.dto.WhoAmIResponse;
import com.ugrocapital.losbot.dto.WsErrorResponse;
import com.ugrocapital.losbot.chat.ChatEngineService;
import com.ugrocapital.losbot.directquery.DirectQueryException;
import com.ugrocapital.losbot.directquery.DirectQueryService;
import com.ugrocapital.losbot.log.FeedbackLogService;
import com.ugrocapital.losbot.log.QueryLogService;

@Component
public class LosWebSocketHandler extends TextWebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(LosWebSocketHandler.class);

    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final AuthService authService;
    private final ChatEngineService chatEngine;
    private final DirectQueryService directQuery;
    private final GreetingService greetingService;
    private final QueryLogService queryLog;
    private final FeedbackLogService feedbackLog;

    public LosWebSocketHandler(ObjectMapper objectMapper, Validator validator, AuthService authService, ChatEngineService chatEngine,
            DirectQueryService directQuery, GreetingService greetingService, QueryLogService queryLog,
            FeedbackLogService feedbackLog) {
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.authService = authService;
        this.chatEngine = chatEngine;
        this.directQuery = directQuery;
        this.greetingService = greetingService;
        this.queryLog = queryLog;
        this.feedbackLog = feedbackLog;
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        String type = null;
        try {
            JsonNode envelope = objectMapper.readTree(message.getPayload());
            type = requiredText(envelope, "type");
            JsonNode payload = envelope.path("payload");
            Object response = dispatch(type, payload);
            send(session, type, response);
        } catch (Exception exception) {
            log.warn("WebSocket request failed for type {}", type, exception);
            send(session, type == null ? "error" : type,
                    new ErrorPayload(new WsErrorResponse(errorCode(exception), publicMessage(exception))));
        }
    }

    private Object dispatch(String type, JsonNode payload) throws IOException {
        return switch (type) {
        case "chat" -> chat(payload);
        case "chat.direct" -> direct(payload);
        case "greet" -> new GreetingResponse(greetingService.buildGreeting(sessionId(payload)));
        case "whoami" -> whoami(payload);
        case "chat.reset" -> reset(payload);
        case "feedback" -> feedback(payload);
        case "feedback.detail" -> feedbackDetail(payload);
        default -> throw new IllegalArgumentException("Unsupported message type.");
        };
    }

    private ChatResponse chat(JsonNode payload) throws IOException {
        ChatRequest request = objectMapper.treeToValue(payload, ChatRequest.class);
        validate(request);
        AuthContext auth = authService.getAuthContext(request.sessionId());
        ChatEngineService.ChatResult result = chatEngine.runChatTurnResult(
            auth, request.message(), request.isHandoff(), request.sessionId());
        Integer queryId = queryLog.logQuery(request.message(), result.reply(), "chat");
        return new ChatResponse(result.reply(), request.sessionId(), result.hasMore(), queryId);
    }

    private DirectChatResponse direct(JsonNode payload) throws IOException {
        DirectChatRequest request = objectMapper.treeToValue(payload, DirectChatRequest.class);
        validate(request);
        AuthContext auth = authService.getAuthContext(request.sessionId());
        String reply;
        boolean hasMore;
        try {
            DirectQueryService.DirectQueryResult result = directQuery.runDirectQueryResult(
                    request.field(), request.value(), request.offset(), auth);
            if (result == null) {
                reply = directQuery.runDirectQuery(request.field(), request.value(), request.offset(), auth);
                hasMore = false;
            } else {
                reply = result.reply();
                hasMore = result.hasMore();
            }
        } catch (DirectQueryException exception) {
            reply = exception.getMessage();
            hasMore = false;
        }
        Integer queryId = queryLog.logQuery(request.value(), reply, "direct");
        return new DirectChatResponse(reply, request.sessionId(), hasMore, queryId);
    }

    private WhoAmIResponse whoami(JsonNode payload) {
        AuthContext auth = authService.getAuthContext(sessionId(payload));
        return new WhoAmIResponse(auth.username(), auth.role(), authService.displayName(auth));
    }

    private ResetResponse reset(JsonNode payload) {
        AuthContext auth = authService.getAuthContext(sessionId(payload));
        chatEngine.reset(auth);
        return new ResetResponse("reset");
    }

    private FeedbackResponse feedback(JsonNode payload) throws IOException {
        FeedbackRequest request = objectMapper.treeToValue(payload, FeedbackRequest.class);
        validate(request);
        if (request.queryId() == null) {
            throw new IllegalArgumentException("A query id is required.");
        }
        queryLog.addFeedback(request.queryId(), request.positive());
        return new FeedbackResponse(true);
    }

    private FeedbackDetailResponse feedbackDetail(JsonNode payload) throws IOException {
        FeedbackDetailRequest request = objectMapper.treeToValue(payload, FeedbackDetailRequest.class);
        validate(request);
        if ((request.tags() == null || request.tags().isEmpty())
                && (request.comment() == null || request.comment().isBlank())) {
            throw new IllegalArgumentException("Please provide at least one feedback tag or a comment.");
        }
        Long id = feedbackLog.addFeedbackDetail(request.queryId(), request.queryText(), request.answerText(),
                request.positive(), request.tags(), request.comment());
        return new FeedbackDetailResponse(id != null, id);
    }

    private void send(WebSocketSession session, String type, Object payload) throws IOException {
        Map<String, Object> envelope = new HashMap<>();
        envelope.put("type", type);
        envelope.put("payload", payload);
        synchronized (session) {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(envelope)));
            }
        }
    }

    private String sessionId(JsonNode payload) {
        return requiredText(payload, "session_id");
    }

    private <T> void validate(T request) {
        Set<ConstraintViolation<T>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new IllegalArgumentException(violations.iterator().next().getMessage());
        }
    }

    private String requiredText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.asText().isBlank()) {
            throw new IllegalArgumentException("Missing " + field + ".");
        }
        return value.asText();
    }

    private String errorCode(Exception exception) {
        return exception.getClass().getSimpleName().equals("UnknownSessionException")
                ? "UNKNOWN_SESSION" : exception instanceof IllegalArgumentException ? "BLANK_MESSAGE" : "INTERNAL_ERROR";
    }

    private String publicMessage(Exception exception) {
        String code = errorCode(exception);
        if ("INTERNAL_ERROR".equals(code)) {
            return "Something went wrong. Please try again.";
        }
        if ("UNKNOWN_SESSION".equals(code)) {
            return "Session not recognized.";
        }
        return exception.getMessage();
    }

    private record ErrorPayload(WsErrorResponse error) {
    }
}