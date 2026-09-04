package com.ugrocapital.losbot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record WhoAmIResponse(
        String username,
        String role,
        @JsonProperty("display_name") String displayName) {
}