package com.ugrocapital.losbot.llm;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.ugrocapital.losbot.config.AppProperties;

@Service
public class LlmClient {
    private static final Logger log = LoggerFactory.getLogger(LlmClient.class);
    private final RestClient restClient;
    private final AppProperties.Llm properties;

    public LlmClient(RestClient.Builder restClientBuilder, AppProperties properties) {
        this.restClient = restClientBuilder.build();
        this.properties = properties.llm();
    }

    @SuppressWarnings("unchecked")
    public LlmResponse call(List<ChatMessage> messages, List<Map<String, Object>> tools) {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            throw new LlmException("LLM API key is not configured.");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", properties.model());
        payload.put("messages", messages.stream().map(ChatMessage::toWireFormat).toList());
        payload.put("temperature", 0);
        if (tools != null && !tools.isEmpty()) {
            payload.put("tools", tools);
            payload.put("tool_choice", "auto");
        }

        try {
            Map<String, Object> response = restClient.post()
                    .uri(properties.url())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + properties.apiKey())
                    .body(payload)
                    .retrieve()
                    .body(Map.class);
            if (response == null || !(response.get("choices") instanceof List<?> choices) || choices.isEmpty()
                    || !(choices.getFirst() instanceof Map<?, ?> choice)
                    || !(choice.get("message") instanceof Map<?, ?> message)) {
                throw new LlmException("LLM response did not contain an assistant message.");
            }
            Map<String, Object> assistant = new LinkedHashMap<>();
            message.forEach((key, value) -> assistant.put(String.valueOf(key), value));
            Map<String, Object> usage = response.get("usage") instanceof Map<?, ?> rawUsage
                    ? toStringObjectMap(rawUsage)
                    : Map.of();
                long promptTokens = tokenCount(usage, "prompt_tokens");
                long completionTokens = tokenCount(usage, "completion_tokens");
                long totalTokens = usage.containsKey("total_tokens")
                    ? tokenCount(usage, "total_tokens") : promptTokens + completionTokens;
                log.info("LLM token usage: prompt={}, completion={}, total={}",
                    promptTokens, completionTokens, totalTokens);
            return new LlmResponse(assistant, usage);
        } catch (LlmException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new LlmException("LLM request failed.", exception);
        }
    }

    private Map<String, Object> toStringObjectMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private long tokenCount(Map<String, Object> usage, String name) {
        Object value = usage.get(name);
        return value instanceof Number number ? number.longValue() : 0L;
    }

    public record LlmResponse(Map<String, Object> assistantMessage, Map<String, Object> usage) {
    }
}