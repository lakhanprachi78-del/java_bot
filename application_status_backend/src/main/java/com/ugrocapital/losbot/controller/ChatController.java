package com.ugrocapital.losbot.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.ugrocapital.losbot.auth.AuthContext;
import com.ugrocapital.losbot.auth.AuthService;
import com.ugrocapital.losbot.chat.ChatEngineService;
import com.ugrocapital.losbot.dto.ChatRequest;
import com.ugrocapital.losbot.dto.ChatResponse;
import com.ugrocapital.losbot.log.QueryLogService;

import jakarta.validation.Valid;

@RestController
public class ChatController {
    private final AuthService authService;
    private final ChatEngineService chatEngine;
    private final QueryLogService queryLog;

    public ChatController(AuthService authService, ChatEngineService chatEngine, QueryLogService queryLog) {
        this.authService = authService;
        this.chatEngine = chatEngine;
        this.queryLog = queryLog;
    }

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        AuthContext auth = authService.getAuthContext(request.sessionId());
        ChatEngineService.ChatResult result = chatEngine.runChatTurnResult(
                auth, request.message(), request.isHandoff(), request.sessionId());
        Integer queryId = queryLog.logQuery(request.message(), result.reply(), "chat");
        return ResponseEntity.ok(new ChatResponse(result.reply(), request.sessionId(), result.hasMore(), queryId));
    }
}