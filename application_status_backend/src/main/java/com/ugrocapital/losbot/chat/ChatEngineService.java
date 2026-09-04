package com.ugrocapital.losbot.chat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ugrocapital.losbot.auth.AuthContext;
import com.ugrocapital.losbot.config.AppProperties;
import com.ugrocapital.losbot.format.ReplyFormatter;
import com.ugrocapital.losbot.llm.ChatMessage;
import com.ugrocapital.losbot.llm.LlmClient;
import com.ugrocapital.losbot.repository.ApplicationDto;
import com.ugrocapital.losbot.repository.ApplicationQueryRepository;
import com.ugrocapital.losbot.repository.SearchResult;
import com.ugrocapital.losbot.tools.ToolDispatcher;
import com.ugrocapital.losbot.tools.ToolSchemas;

@Service
public class ChatEngineService {
    private static final Logger log = LoggerFactory.getLogger(ChatEngineService.class);
    private static final String SHARED_HANDOFF_FALLBACK =
            "I couldn't help with that request here. Please ask about loan applications or SkaleUp.";
    private static final int MAX_TOOL_ROUNDS = 4;
    private static final int MAX_HISTORY_MESSAGES = 20;
    private static final Set<String> SEARCH_TOOLS = Set.of(
            "search_by_applicant_name", "search_by_applicant_email", "search_by_created_by",
            "search_by_date", "search_by_date_range", "search_by_status");
    private static final Set<String> LOOKUP_TOOLS = Set.of("get_application_by_id");
    private static final String STATIC_SYSTEM_PROMPT = """
            You are an assistant for UGRO Capital's Loan Origination System (LOS).
            Answer questions about loan applications using the available tools.
            Never invent application data. Use a tool when application data is requested.
            If the question is unrelated to LOS applications, call not_in_scope.
            Keep replies concise and clear.
            """;

    private final LlmClient llmClient;
    private final ToolDispatcher toolDispatcher;
    private final ObjectMapper objectMapper;
    private final AppProperties properties;
    private final ApplicationQueryRepository applicationRepository;
    private final RestClient restClient;
    private final Map<String, List<ChatMessage>> conversations = new ConcurrentHashMap<>();

    public ChatEngineService(LlmClient llmClient, ToolDispatcher toolDispatcher, ObjectMapper objectMapper,
            AppProperties properties, ApplicationQueryRepository applicationRepository, RestClient.Builder restClientBuilder) {
        this.llmClient = llmClient;
        this.toolDispatcher = toolDispatcher;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.applicationRepository = applicationRepository;
        this.restClient = restClientBuilder.build();
    }

    public String runChatTurn(AuthContext auth, String message) {
        return runChatTurn(auth, message, false);
    }

    public String runChatTurn(AuthContext auth, String message, boolean isHandoff) {
        return runChatTurnResult(auth, message, isHandoff).reply();
    }

    public ChatResult runChatTurnResult(AuthContext auth, String message, boolean isHandoff) {
        return runChatTurnResult(auth, message, isHandoff, auth == null ? null : auth.username());
    }

    public ChatResult runChatTurnResult(AuthContext auth, String message, boolean isHandoff, String sessionId) {
        if (auth == null || message == null || message.isBlank()) {
            throw new IllegalArgumentException("Please enter a message.");
        }
        ChatResult deterministicResult = deterministicCreatedByMeLastMonth(auth, message);
        if (deterministicResult != null) {
            persist(auth, message, deterministicResult.reply());
            return deterministicResult;
        }
        if (SmallTalkDetector.isPureSmallTalk(message)) {
            String reply = smallTalkReply(message);
            persist(auth, message, reply);
            return new ChatResult(reply, false);
        }

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.text("system", systemPrompt(auth)));
        messages.addAll(history(auth));
        messages.add(ChatMessage.text("user", message.trim()));

        TokenAccumulator tokens = new TokenAccumulator();
        boolean hasMoreResults = false;
        for (int round = 0; round < MAX_TOOL_ROUNDS; round++) {
            LlmClient.LlmResponse response = llmClient.call(messages, ToolSchemas.SCHEMAS);
            addUsage(tokens, response.usage());
                log.info("Query token usage so far: prompt={}, completion={}, total={}",
                    tokens.promptTokens(), tokens.completionTokens(), tokens.totalTokens());
            Map<String, Object> assistant = response.assistantMessage();
            List<Map<String, Object>> toolCalls = toolCalls(assistant);
            if (toolCalls.isEmpty()) {
                String reply = text(assistant);
                persist(auth, message, reply);
                return new ChatResult(reply, hasMoreResults);
            }

            if (toolCalls.size() == 1) {
                Map<String, Object> call = toolCalls.get(0);
                String name = toolName(call);
                if ("not_in_scope".equals(name)) {
                    if (!isHandoff) {
                        return handoff(auth, message, sessionId);
                    }
                    return new ChatResult(SHARED_HANDOFF_FALLBACK, false);
                }
                if (SEARCH_TOOLS.contains(name) || LOOKUP_TOOLS.contains(name)) {
                    Object result = toolDispatcher.dispatch(name, arguments(call), auth);
                    ChatResult chatResult = formatFastPathResult(result);
                    persist(auth, message, chatResult.reply());
                    return chatResult;
                }
            }

            messages.add(fromAssistant(assistant));
            for (Map<String, Object> call : toolCalls) {
                String name = toolName(call);
                if ("not_in_scope".equals(name)) {
                    if (!isHandoff) {
                        return handoff(auth, message, sessionId);
                    }
                    return new ChatResult(SHARED_HANDOFF_FALLBACK, false);
                }
                Object result = toolDispatcher.dispatch(name, arguments(call), auth);
                if (result instanceof SearchResult searchResult) {
                    hasMoreResults = hasMoreResults || searchResult.hasMore();
                }
                messages.add(new ChatMessage("tool", json(result), null, name, string(call.get("id"))));
            }
        }

        String reply = "I couldn't complete that search. Please try narrowing your question.";
        persist(auth, message, reply);
        return new ChatResult(reply, false);
    }

    public void reset(AuthContext auth) {
        if (auth != null) {
            conversations.remove(conversationKey(auth));
        }
    }

    private String systemPrompt(AuthContext auth) {
        return STATIC_SYSTEM_PROMPT + "\n## SESSION CONTEXT\nusername: " + auth.username()
                + "\nrole: " + auth.role() + "\ntoday: " + java.time.LocalDate.now()
                + "\nsupport email: " + properties.support().email();
    }

    private List<ChatMessage> history(AuthContext auth) {
        return new ArrayList<>(conversations.getOrDefault(conversationKey(auth), Collections.emptyList()));
    }

    private void persist(AuthContext auth, String userMessage, String reply) {
        List<ChatMessage> history = conversations.computeIfAbsent(conversationKey(auth), key -> new ArrayList<>());
        synchronized (history) {
            history.add(ChatMessage.text("user", userMessage.trim()));
            history.add(ChatMessage.text("assistant", reply));
            while (history.size() > MAX_HISTORY_MESSAGES) {
                history.remove(0);
                if (!history.isEmpty() && "assistant".equals(history.get(0).role())) {
                    history.remove(0);
                }
            }
        }
    }

    private String conversationKey(AuthContext auth) {
        return auth.username().trim().toLowerCase();
    }

    private String smallTalkReply(String message) {
        return message.trim().toLowerCase().startsWith("thank") ? "You're welcome!" : "How can I help with your loan applications?";
    }

    private String formatFastPath(Object result) {
        return formatFastPathResult(result).reply();
    }

    private ChatResult formatFastPathResult(Object result) {
        if (result instanceof java.util.Optional<?> optional) {
            return new ChatResult(ReplyFormatter.formatSingleLookupReply((ApplicationDto) optional.orElse(null)), false);
        }
        if (result instanceof SearchResult searchResult) {
            return new ChatResult(ReplyFormatter.formatSearchReply(searchResult), searchResult.hasMore());
        }
        return new ChatResult(String.valueOf(result), false);
    }

    private ChatResult deterministicCreatedByMeLastMonth(AuthContext auth, String message) {
        String normalized = message.toLowerCase().replaceAll("\\s+", " ").trim();
        if (!normalized.contains("last month")
                || !(normalized.contains("created by me") || normalized.contains("created by myself"))) {
            return null;
        }

        LocalDate previousMonth = LocalDate.now().minusMonths(1);
        LocalDate start = previousMonth.with(TemporalAdjusters.firstDayOfMonth());
        LocalDate end = previousMonth.with(TemporalAdjusters.lastDayOfMonth());
        int offset = extractOffset(normalized);
        SearchResult result = applicationRepository.searchByDateRange(start, end, auth, offset, 5);
        return new ChatResult(ReplyFormatter.formatSearchReply(result), result.hasMore());
    }

    @SuppressWarnings("unchecked")
    private ChatResult handoff(AuthContext auth, String message, String sessionId) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("message", message.trim());
        request.put("session_id", sessionId == null ? auth.username() : sessionId);
        request.put("language", "en");
        request.put("is_handoff", true);
        log.info("[Handoff] LOS -> RAG (one-hop ticket)");
        try {
            Map<String, Object> response = restClient.post()
                    .uri(properties.handoff().ragChatUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(Map.class);
            String answer = response == null || response.get("answer") == null
                    ? "I couldn't get an answer from SkaleUp right now." : response.get("answer").toString();
            return new ChatResult(answer, false);
        } catch (RuntimeException exception) {
            log.warn("[Handoff] LOS -> RAG failed; returning shared fallback", exception);
            return new ChatResult("I couldn't connect to SkaleUp right now. Please try again.", false);
        }
    }

    private int extractOffset(String message) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("skip the first (\\d+) results").matcher(message);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : 0;
    }

    public record ChatResult(String reply, boolean hasMore) {
    }

    private List<Map<String, Object>> toolCalls(Map<String, Object> assistant) {
        Object raw = assistant.get("tool_calls");
        if (!(raw instanceof Collection<?> calls)) {
            return List.of();
        }
        return calls.stream().filter(Map.class::isInstance).map(call -> (Map<String, Object>) call).toList();
    }

    private String toolName(Map<String, Object> call) {
        Object function = call.get("function");
        if (!(function instanceof Map<?, ?> functionMap) || functionMap.get("name") == null) {
            throw new IllegalArgumentException("Malformed LLM tool call.");
        }
        return functionMap.get("name").toString();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> arguments(Map<String, Object> call) {
        Map<?, ?> function = (Map<?, ?>) call.get("function");
        Object raw = function.get("arguments");
        if (raw instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        try {
            return objectMapper.readValue(String.valueOf(raw), Map.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Malformed LLM tool arguments.", exception);
        }
    }

    private ChatMessage fromAssistant(Map<String, Object> assistant) {
        return new ChatMessage("assistant", assistant.get("content") == null ? null : assistant.get("content").toString(),
                assistant.get("tool_calls"), null, null);
    }

    private String text(Map<String, Object> assistant) {
        return assistant.get("content") == null ? "I wasn't able to produce a response." : assistant.get("content").toString();
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return String.valueOf(value);
        }
    }

    private String string(Object value) {
        return value == null ? null : value.toString();
    }

    private void addUsage(TokenAccumulator tokens, Map<String, Object> usage) {
        tokens.add(number(usage.get("prompt_tokens")), number(usage.get("completion_tokens")));
    }

    private long number(Object value) {
        return value instanceof Number number ? number.longValue() : 0;
    }
}