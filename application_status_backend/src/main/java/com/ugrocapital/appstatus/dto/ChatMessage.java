package com.ugrocapital.appstatus.dto;

/**
 * One {"role", "content"} turn — role is "system" | "user" | "assistant" |
 * "tool". Mirrors the plain dicts used throughout chat_engine.py and
 * stored in main.py's CONVERSATIONS map.
 */
public class ChatMessage {
    public String role;
    public String content;

    // Only populated on assistant messages that made tool calls, and on
    // "tool" role messages replying to one. Kept as raw JSON-ish objects
    // (Map/List via Jackson) rather than typed classes, mirroring how
    // loosely the Python side treats these — they only ever get
    // serialized back out to the LLM API, never inspected field-by-field.
    public Object toolCalls;      // List<Map<String,Object>>, assistant-side
    public String toolCallId;     // tool-role only
    public String name;           // tool-role only (function name)

    public ChatMessage() {
    }

    public ChatMessage(String role, String content) {
        this.role = role;
        this.content = content;
    }

    public static ChatMessage of(String role, String content) {
        return new ChatMessage(role, content);
    }
}
