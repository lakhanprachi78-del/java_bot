package com.ugrocapital.losbot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;

public record ChatRequest(
        @NotBlank @JsonProperty("session_id") String sessionId,
        @NotBlank String message,
        @JsonProperty("is_handoff") boolean isHandoff) {
}