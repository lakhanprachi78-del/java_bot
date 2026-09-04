package com.ugrocapital.losbot.websocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;

import jakarta.validation.Validator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ugrocapital.losbot.auth.AuthService;
import com.ugrocapital.losbot.auth.GreetingService;
import com.ugrocapital.losbot.chat.ChatEngineService;
import com.ugrocapital.losbot.directquery.DirectQueryException;
import com.ugrocapital.losbot.directquery.DirectQueryService;
import com.ugrocapital.losbot.log.FeedbackLogService;
import com.ugrocapital.losbot.log.QueryLogService;

class LosWebSocketHandlerTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Validator validator = mock(Validator.class);
    private final AuthService authService = new AuthService();
    private final ChatEngineService chatEngine = mock(ChatEngineService.class);
    private final DirectQueryService directQuery = mock(DirectQueryService.class);
    private final GreetingService greetingService = new GreetingService(authService);
    private final QueryLogService queryLog = mock(QueryLogService.class);
    private final FeedbackLogService feedbackLog = mock(FeedbackLogService.class);
    private final WebSocketSession session = mock(WebSocketSession.class);
    private LosWebSocketHandler handler;

    @BeforeEach
    void setUp() {
        when(validator.validate(any())).thenReturn(Set.of());
        handler = new LosWebSocketHandler(objectMapper, validator, authService, chatEngine, directQuery, greetingService,
                queryLog, feedbackLog);
    }

    @Test
    void returnsWhoAmIUsingTheEnvelopeType() throws Exception {
        when(session.isOpen()).thenReturn(true);

        handler.handleTextMessage(session, new TextMessage(
                "{\"type\":\"whoami\",\"payload\":{\"session_id\":\"session-chandan\"}}"));

        JsonNode response = sentJson();
        assertEquals("whoami", response.get("type").asText());
        assertEquals("chandan", response.at("/payload/username").asText());
        assertEquals("user", response.at("/payload/role").asText());
    }

    @Test
    void returnsDirectValidationErrorsAsNormalReplies() throws Exception {
        when(session.isOpen()).thenReturn(true);
        when(directQuery.runDirectQuery(eq("status"), eq("bad"), eq(0), any()))
                .thenThrow(new DirectQueryException("Unsupported direct-query field: status"));
        when(queryLog.logQuery("bad", "Unsupported direct-query field: status", "direct")).thenReturn(4);

        handler.handleTextMessage(session, new TextMessage(
                "{\"type\":\"chat.direct\",\"payload\":{\"session_id\":\"session-chandan\",\"field\":\"status\",\"value\":\"bad\"}}"));

        JsonNode response = sentJson();
        assertEquals("chat.direct", response.get("type").asText());
        assertEquals("Unsupported direct-query field: status", response.at("/payload/reply").asText());
        assertEquals(4, response.at("/payload/query_id").asInt());
    }

    @Test
    void mapsUnknownSessionsToAStableErrorEnvelope() throws Exception {
        when(session.isOpen()).thenReturn(true);

        handler.handleTextMessage(session, new TextMessage(
                "{\"type\":\"greet\",\"payload\":{\"session_id\":\"unknown\"}}"));

        JsonNode response = sentJson();
        assertEquals("greet", response.get("type").asText());
        assertEquals("UNKNOWN_SESSION", response.at("/payload/error/code").asText());
        assertEquals("Session not recognized.", response.at("/payload/error/message").asText());
    }

    private JsonNode sentJson() throws Exception {
        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(captor.capture());
        return objectMapper.readTree(captor.getValue().getPayload());
    }
}