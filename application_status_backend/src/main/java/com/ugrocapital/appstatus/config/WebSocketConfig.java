package com.ugrocapital.appstatus.config;

import com.ugrocapital.appstatus.web.ChatWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Registers the /ws endpoint the Angular frontend connects to
 * (environment.ts: losWsUrl = 'ws://127.0.0.1:5000/ws'). There is no
 * equivalent of this in the Python backend you gave me — that project
 * exposed plain REST endpoints. This class + ChatWebSocketHandler is the
 * bridge that lets your existing Angular frontend, which only ever
 * speaks {type, payload} over a WebSocket, talk to the same ported
 * business logic.
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final ChatWebSocketHandler chatWebSocketHandler;

    public WebSocketConfig(ChatWebSocketHandler chatWebSocketHandler) {
        this.chatWebSocketHandler = chatWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(chatWebSocketHandler, "/ws")
                .setAllowedOriginPatterns("*"); // tighten before production
    }
}
