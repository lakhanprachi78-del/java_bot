package com.ugrocapital.losbot.llm;

import java.util.Map;

public record ChatMessage(
        String role,
        String content,
        Object toolCalls,
        String name,
        String toolCallId) {

    public static ChatMessage text(String role, String content) {
        return new ChatMessage(role, content, null, null, null);
    }

    public Map<String, Object> toWireFormat() {
        java.util.Map<String, Object> message = new java.util.LinkedHashMap<>();
        message.put("role", role);
        if (content != null) {
            message.put("content", content);
        }
        if (toolCalls != null) {
            message.put("tool_calls", toolCalls);
        }
        if (name != null) {
            message.put("name", name);
        }
        if (toolCallId != null) {
            message.put("tool_call_id", toolCallId);
        }
        return message;
    }
}