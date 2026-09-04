package com.ugrocapital.losbot.chat;

public class TokenAccumulator {
    private long promptTokens;
    private long completionTokens;

    public void add(long prompt, long completion) {
        promptTokens += Math.max(0, prompt);
        completionTokens += Math.max(0, completion);
    }

    public long promptTokens() {
        return promptTokens;
    }

    public long completionTokens() {
        return completionTokens;
    }

    public long totalTokens() {
        return promptTokens + completionTokens;
    }
}