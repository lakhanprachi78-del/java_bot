package com.ugro.skaleup.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class RagWebSocketConfig implements WebSocketConfigurer {

    private final RagService rag;
    private final QueryCacheService cache;
    private final ObjectMapper mapper;

    public RagWebSocketConfig(RagService rag, QueryCacheService cache, ObjectMapper mapper) {
        this.rag = rag;
        this.cache = cache;
        this.mapper = mapper;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Same path the frontend already expects: ws://host:8000/ws
        registry.addHandler(ragWebSocketHandler(), "/ws")
                .setAllowedOrigins("*"); // matches @CrossOrigin(origins="*") on RagController
    }

    @Bean
    public RagWebSocketHandler ragWebSocketHandler() {
        return new RagWebSocketHandler(rag, cache, mapper);
    }
}