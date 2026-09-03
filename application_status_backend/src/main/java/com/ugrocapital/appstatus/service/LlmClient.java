package com.ugrocapital.appstatus.service;

import com.ugrocapital.appstatus.config.AppProperties;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Thin wrapper around the LLM chat-completions endpoint (Gemini's
 * OpenAI-compatible API — same endpoint the Python "OpenRouter" naming
 * actually pointed at). Keeps HTTP/auth details out of the conversation
 * logic. Direct port of llm_client.py.
 */
@Component
public class LlmClient {

    /** One LLM call's result: the raw assistant "message" object (may
     * contain "content" text or a "tool_calls" list) plus usage stats. */
    public record LlmCallResult(Map<String, Object> message, Map<String, Object> usage) {
    }

    private final AppProperties appProperties;
    private final RestTemplate restTemplate;

    public LlmClient(AppProperties appProperties) {
        this.appProperties = appProperties;
        this.restTemplate = new RestTemplate();
    }

    @SuppressWarnings("unchecked")
    public LlmCallResult callLlm(List<Map<String, Object>> messages, List<Map<String, Object>> tools) {
        if (appProperties.getLlmApiKey() == null || appProperties.getLlmApiKey().isBlank()) {
            throw new IllegalStateException("LLM_API_KEY is not set. Put it in your environment/.env.");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(appProperties.getLlmApiKey());
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("model", appProperties.getLlmModel());
        payload.put("messages", messages);
        payload.put("temperature", 0);
        if (tools != null && !tools.isEmpty()) {
            payload.put("tools", tools);
            payload.put("tool_choice", "auto");
        }

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

        Map<String, Object> data;
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    AppProperties.LLM_ENDPOINT_URL, request, Map.class);
            data = response.getBody();
        } catch (RestClientResponseException e) {
            throw new RuntimeException("LLM call failed: " + e.getStatusCode() + " " + e.getResponseBodyAsString(), e);
        }

        if (data == null || !data.containsKey("choices") || ((List<?>) data.get("choices")).isEmpty()) {
            throw new RuntimeException("Unexpected LLM response: " + data);
        }

        List<Map<String, Object>> choices = (List<Map<String, Object>>) data.get("choices");
        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        Map<String, Object> usage = (Map<String, Object>) data.get("usage");

        TokenTracker.logUsage("LOS LLM", usage);

        return new LlmCallResult(message, usage);
    }
}
