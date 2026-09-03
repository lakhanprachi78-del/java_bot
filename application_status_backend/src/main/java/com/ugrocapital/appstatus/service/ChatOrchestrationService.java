package com.ugrocapital.appstatus.service;

import com.ugrocapital.appstatus.auth.AuthContext;
import com.ugrocapital.appstatus.auth.AuthService;
import com.ugrocapital.appstatus.auth.UnknownSessionException;
import com.ugrocapital.appstatus.dto.ChatMessage;
import com.ugrocapital.appstatus.dto.web.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Request-level orchestration for a chat turn and a direct-query turn —
 * exact behavioral port of main.py's POST /chat and POST /chat/direct
 * handler bodies (auth resolution, history bookkeeping, out-of-scope
 * handoff, query logging). Both the WebSocket handler (ChatWebSocketHandler,
 * used by the real Angular frontend) and the plain REST controller
 * (ChatController, kept for parity/testing) delegate here so the actual
 * business logic exists in exactly one place.
 */
@Service
public class ChatOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(ChatOrchestrationService.class);

    private final AuthService authService;
    private final ChatEngine chatEngine;
    private final ConversationStore conversationStore;
    private final HandoffService handoffService;
    private final DirectQueryService directQueryService;
    private final QueryLogService queryLogService;

    public ChatOrchestrationService(
            AuthService authService,
            ChatEngine chatEngine,
            ConversationStore conversationStore,
            HandoffService handoffService,
            DirectQueryService directQueryService,
            QueryLogService queryLogService
    ) {
        this.authService = authService;
        this.chatEngine = chatEngine;
        this.conversationStore = conversationStore;
        this.handoffService = handoffService;
        this.directQueryService = directQueryService;
        this.queryLogService = queryLogService;
    }

    /** Thrown for a 401-equivalent (unknown/expired session_id). */
    public static class UnauthorizedException extends RuntimeException {
        public UnauthorizedException(String message) {
            super(message);
        }
    }

    /** Thrown for a 400-equivalent (bad request body). */
    public static class BadRequestException extends RuntimeException {
        public BadRequestException(String message) {
            super(message);
        }
    }

    // --- /chat --------------------------------------------------------

    public ChatResponse chat(String sessionId, String message, boolean isHandoff) {
        if (message == null || message.strip().isEmpty()) {
            throw new BadRequestException("message must not be empty");
        }

        AuthContext auth;
        try {
            auth = authService.getAuthContext(sessionId);
        } catch (UnknownSessionException e) {
            throw new UnauthorizedException("Invalid or expired session.");
        }

        List<ChatMessage> history = conversationStore.get(sessionId);
        String reply;
        List<ChatMessage> updatedHistory;
        Boolean hasMore;

        try {
            ChatEngine.ChatTurnResult result = chatEngine.runChatTurn(history, message, auth);
            reply = result.reply();
            updatedHistory = result.updatedHistory();
            hasMore = result.hasMore();
        } catch (OutOfScopeException e) {
            // e carries the clean, isolated out-of-scope question (the
            // not_in_scope tool's "topic" argument) — NOT the raw
            // message, which may have extra application-search context
            // glued onto it by the frontend (e.g. an active status filter).
            String cleanTopic = e.getMessage() != null && !e.getMessage().isBlank() ? e.getMessage().strip() : message;

            if (isHandoff) {
                // This message already arrived here as a handoff from
                // SkaleUp, and we ALSO think it's out of scope — neither
                // bot can help. Don't forward again (infinite ping-pong).
                reply = HandoffService.NO_SCOPE_ANYWHERE_MESSAGE;
            } else {
                reply = handoffService.handoffToSkaleUp(sessionId, cleanTopic);
            }

            updatedHistory = new ArrayList<>(history);
            updatedHistory.add(ChatMessage.of("user", message));
            updatedHistory.add(ChatMessage.of("assistant", reply));
            hasMore = null;
        } catch (Exception e) {
            // Log the real error server-side only. Never return exception
            // internals to the client.
            log.error("ERROR: {}", e.getMessage(), e);
            throw new RuntimeException("Something went wrong processing that request. Please try again.");
        }

        conversationStore.put(sessionId, updatedHistory);
        Long queryId = queryLogService.logQuery(message, reply, "chat");
        return new ChatResponse(reply, sessionId, hasMore, queryId);
    }

    // --- /chat/direct ---------------------------------------------------

    public ChatResponse chatDirect(String sessionId, String field, String value, int offset) {
        AuthContext auth;
        try {
            auth = authService.getAuthContext(sessionId);
        } catch (UnknownSessionException e) {
            throw new UnauthorizedException("Invalid or expired session.");
        }

        String displayMessage = "[" + field + "] " + value + (offset != 0 ? " (offset " + offset + ")" : "");

        String reply;
        Boolean hasMore;
        try {
            DirectQueryService.DirectQueryResult result = directQueryService.runDirectQuery(field, value, offset, auth);
            reply = result.reply();
            hasMore = result.hasMore();
        } catch (DirectQueryException e) {
            // Same shape as a normal reply — the frontend doesn't need to
            // know this request never touched the LLM.
            reply = e.getMessage();
            Long queryId = queryLogService.logQuery(displayMessage, reply, "direct");
            return new ChatResponse(reply, sessionId, null, queryId);
        }

        // Keep this turn in the same conversation history the LLM path
        // reads, so a follow-up natural-language message (e.g. adding an
        // extra detail to a status filter) still has full context and
        // pagination keeps working across the direct/LLM boundary.
        List<ChatMessage> history = conversationStore.get(sessionId);
        List<ChatMessage> updated = new ArrayList<>(history);
        updated.add(ChatMessage.of("user", displayMessage));
        updated.add(ChatMessage.of("assistant", reply));
        conversationStore.put(sessionId, updated);

        Long queryId = queryLogService.logQuery(displayMessage, reply, "direct");
        return new ChatResponse(reply, sessionId, hasMore, queryId);
    }

    public void resetSession(String sessionId) {
        conversationStore.reset(sessionId);
    }
}
