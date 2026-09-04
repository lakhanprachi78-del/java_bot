package com.ugrocapital.losbot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record DirectChatRequest(
        @NotBlank @JsonProperty("session_id") String sessionId,
        @NotBlank String field,
        @NotBlank String value,
        @Min(0) int offset) {
}