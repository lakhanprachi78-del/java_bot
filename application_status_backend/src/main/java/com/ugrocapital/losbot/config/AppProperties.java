package com.ugrocapital.losbot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "los")
public record AppProperties(
        Database database,
        Llm llm,
        App app,
        Handoff handoff,
        Support support) {

    public record Database(String url, String username, String password) {
    }

    public record Llm(String apiKey, String url, String model) {
    }

    public record App(int maxRowsReturned) {
    }

    public record Handoff(String ragChatUrl) {
    }

    public record Support(String email) {
    }
}