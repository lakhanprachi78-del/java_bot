package com.ugrocapital.losbot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record FeedbackRequest(
        @JsonProperty("query_id") Integer queryId,
        boolean positive) {
}