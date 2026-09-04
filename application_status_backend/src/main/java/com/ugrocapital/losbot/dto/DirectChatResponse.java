package com.ugrocapital.losbot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record DirectChatResponse(
        String reply,
        @JsonProperty("session_id") String sessionId,
        @JsonProperty("has_more") boolean hasMore,
        @JsonProperty("query_id") Integer queryId) {
}