package com.ugrocapital.losbot.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.Size;

public record FeedbackDetailRequest(
        @JsonProperty("query_id") Integer queryId,
        @JsonProperty("query_text") String queryText,
        @JsonProperty("answer_text") String answerText,
        boolean positive,
        List<String> tags,
        @Size(max = 2000) String comment) {
}