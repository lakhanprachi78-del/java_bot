package com.ugrocapital.appstatus.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Central config. Everything environment-specific lives here and ONLY
 * here — direct equivalent of config.py. Values are bound from OS
 * environment variables (DATABASE_URL, LLM_API_KEY, LLM_MODEL, ...),
 * same names as the old .env file.
 */
@Component
public class AppProperties {

    @Value("${app.database-url}")
    private String databaseUrl;

    @Value("${app.llm-api-key}")
    private String llmApiKey;

    @Value("${app.llm-model}")
    private String llmModel;

    @Value("${app.max-rows-returned}")
    private int maxRowsReturned;

    @Value("${app.rag-chat-url}")
    private String ragChatUrl;

    @Value("${app.support-email}")
    private String supportEmail;

    @Value("${app.query-log-db-path}")
    private String queryLogDbPath;

    @Value("${app.feedback-log-db-path}")
    private String feedbackLogDbPath;

    // The LLM endpoint is not configurable in the Python version either
    // (OPENROUTER_URL is a hardcoded constant pointing at Gemini's
    // OpenAI-compatible endpoint, despite the "OpenRouter" naming) — kept
    // identical here on purpose.
    public static final String LLM_ENDPOINT_URL =
            "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions";

    public String getDatabaseUrl() {
        return databaseUrl;
    }

    public String getLlmApiKey() {
        return llmApiKey;
    }

    public String getLlmModel() {
        return llmModel;
    }

    public int getMaxRowsReturned() {
        return maxRowsReturned;
    }

    public String getRagChatUrl() {
        return ragChatUrl;
    }

    public String getSupportEmail() {
        return supportEmail;
    }

    public String getQueryLogDbPath() {
        return queryLogDbPath;
    }

    public String getFeedbackLogDbPath() {
        return feedbackLogDbPath;
    }
}
