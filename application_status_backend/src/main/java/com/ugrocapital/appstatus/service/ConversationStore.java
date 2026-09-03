package com.ugrocapital.appstatus.service;

import com.ugrocapital.appstatus.dto.ChatMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory conversation history store: session_id -> list of
 * {"role","content"} messages. Direct port of main.py's CONVERSATIONS
 * dict.
 *
 * NOTE on sessions/history: kept in memory, fine for local testing but
 * won't survive a restart or work across multiple instances/replicas.
 * Swap this for Redis or a DB table in production.
 */
@Component
public class ConversationStore {

    private final ConcurrentHashMap<String, List<ChatMessage>> conversations = new ConcurrentHashMap<>();

    public List<ChatMessage> get(String sessionId) {
        return conversations.getOrDefault(sessionId, List.of());
    }

    public void put(String sessionId, List<ChatMessage> history) {
        conversations.put(sessionId, new ArrayList<>(history));
    }

    public void reset(String sessionId) {
        conversations.remove(sessionId);
    }
}
