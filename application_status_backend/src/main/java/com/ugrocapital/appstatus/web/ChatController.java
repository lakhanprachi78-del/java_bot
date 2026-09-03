package com.ugrocapital.appstatus.web;

import com.ugrocapital.appstatus.auth.AuthContext;
import com.ugrocapital.appstatus.auth.AuthService;
import com.ugrocapital.appstatus.auth.UnknownSessionException;
import com.ugrocapital.appstatus.dto.web.*;
import com.ugrocapital.appstatus.service.ChatOrchestrationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * REST parity port of main.py's endpoints. NOT used by the real Angular
 * frontend (it talks WebSocket — see ChatWebSocketHandler) but kept here
 * so the same functionality is reachable via plain HTTP for testing,
 * curl/Postman, or any future non-WebSocket client, and so this project
 * stays a faithful superset of the original Python API surface.
 */
@RestController
public class ChatController {

    private final AuthService authService;
    private final ChatOrchestrationService chatOrchestrationService;

    public ChatController(AuthService authService, ChatOrchestrationService chatOrchestrationService) {
        this.authService = authService;
        this.chatOrchestrationService = chatOrchestrationService;
    }

    @PostMapping("/chat")
    public ChatResponse chat(@Valid @RequestBody ChatRequest req) {
        try {
            return chatOrchestrationService.chat(req.sessionId, req.message, req.isHandoff);
        } catch (ChatOrchestrationService.BadRequestException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (ChatOrchestrationService.UnauthorizedException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, e.getMessage());
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    @PostMapping("/chat/direct")
    public ChatResponse chatDirect(@Valid @RequestBody DirectChatRequest req) {
        try {
            return chatOrchestrationService.chatDirect(req.sessionId, req.field, req.value, req.offset);
        } catch (ChatOrchestrationService.UnauthorizedException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, e.getMessage());
        }
    }

    @GetMapping("/whoami")
    public WhoAmIResponse whoami(@RequestParam("session_id") String sessionId) {
        AuthContext auth;
        try {
            auth = authService.getAuthContext(sessionId);
        } catch (UnknownSessionException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired session.");
        }
        return new WhoAmIResponse(auth.username(), auth.role(), authService.displayNameFromUsername(auth.username()));
    }

    @PostMapping("/chat/reset")
    public ResponseEntity<?> resetSession(@RequestParam("session_id") String sessionId) {
        chatOrchestrationService.resetSession(sessionId);
        return ResponseEntity.ok(java.util.Map.of("status", "reset"));
    }

    @GetMapping("/greet")
    public ResponseEntity<?> greet(@RequestParam("session_id") String sessionId) {
        return ResponseEntity.ok(java.util.Map.of("greeting", authService.buildGreeting(sessionId)));
    }

    @GetMapping("/health")
    public ResponseEntity<?> health() {
        return ResponseEntity.ok(java.util.Map.of("status", "ok"));
    }
}
