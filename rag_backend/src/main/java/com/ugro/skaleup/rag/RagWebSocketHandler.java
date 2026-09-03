package com.ugro.skaleup.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;

public class RagWebSocketHandler extends TextWebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(RagWebSocketHandler.class);

    private final RagService rag;
    private final QueryCacheService cache;
    private final ObjectMapper mapper;

    public RagWebSocketHandler(RagService rag, QueryCacheService cache, ObjectMapper mapper) {
        this.rag = rag;
        this.cache = cache;
        this.mapper = mapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("RAG WebSocket connected: {}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonNode root = mapper.readTree(message.getPayload());
        String type = root.path("type").asText(null);
        JsonNode payload = root.path("payload");

        if (type == null) {
            send(session, "error", Map.of("error", "Missing 'type' on incoming message."));
            return;
        }

        try {
            switch (type) {
                case "chat" -> handleChat(session, payload);
                case "feedback" -> handleFeedback(session, payload);
                case "feedback.detail" -> handleFeedbackDetail(session, payload);
                default -> send(session, type, Map.of("error", "Unsupported message type: " + type));
            }
        } catch (Exception e) {
            log.warn("Error handling '{}' message", type, e);
            send(session, type, Map.of("error", e.getMessage() == null ? "Internal error" : e.getMessage()));
        }
    }

    private void handleChat(WebSocketSession session, JsonNode payload) throws Exception {
        String msg = payload.path("message").asText("").trim();
        String sessionId = payload.path("session_id").asText(null);
        boolean handoff = payload.path("is_handoff").asBoolean(false);

        ApiModels.RagChatResponse response = rag.chat(msg, sessionId, handoff);
        send(session, "chat", response);
    }

    private void handleFeedback(WebSocketSession session, JsonNode payload) {
        Integer queryId = payload.path("query_id").isNull() ? null : payload.path("query_id").asInt();
        boolean positive = payload.path("positive").asBoolean(false);
        boolean ok = cache.recordFeedback(queryId, positive);
        send(session, "feedback", new ApiModels.FeedbackResponse(ok));
    }

    private void handleFeedbackDetail(WebSocketSession session, JsonNode payload) {
        Integer queryId = payload.path("query_id").isNull() ? null : payload.path("query_id").asInt();
        boolean positive = payload.path("positive").asBoolean(false);
        if (queryId != null) {
            cache.recordFeedback(queryId, positive);
        }
        log.info("Feedback detail — query_id={}, positive={}", queryId, positive);
        send(session, "feedback.detail", new ApiModels.FeedbackDetailResponse(true, queryId));
    }

    private void send(WebSocketSession session, String type, Object payload) {
        try {
            String json = mapper.writeValueAsString(Map.of("type", type, "payload", payload));
            session.sendMessage(new TextMessage(json));
        } catch (Exception e) {
            log.error("Failed to send WebSocket message", e);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("WebSocket transport error on session {}: {}", session.getId(), exception.getMessage());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.info("RAG WebSocket closed: {} ({})", session.getId(), status);
    }
}