package com.ugrocapital.appstatus.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Small token usage tracking utility. Direct port of token_tracker.py's
 * TokenAccumulator + log_usage. One instance per chat turn (not a Spring
 * bean — instantiate fresh in ChatEngine.runChatTurn, same lifetime as
 * the Python version's per-call TokenAccumulator()).
 */
public class TokenTracker {

    private static final Logger log = LoggerFactory.getLogger(TokenTracker.class);

    private final Map<String, Long> total = new LinkedHashMap<>();

    public TokenTracker() {
        total.put("prompt_tokens", 0L);
        total.put("completion_tokens", 0L);
        total.put("total_tokens", 0L);
    }

    @SuppressWarnings("unchecked")
    public void add(Map<String, Object> usage) {
        if (usage == null) return;
        for (String key : total.keySet()) {
            Object value = usage.get(key);
            long asLong = value == null ? 0L : ((Number) value).longValue();
            total.merge(key, asLong, Long::sum);
        }
    }

    public Map<String, Long> logTotal(String label) {
        log.info("{} total usage: {}", label, total);
        return new LinkedHashMap<>(total);
    }

    /** Prints usage info in a simple, readable format — mirrors
     * token_tracker.py's standalone log_usage function. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> logUsage(String label, Map<String, Object> usage) {
        if (usage == null) return null;
        log.info("{} usage: {}", label, usage);
        return usage;
    }
}
