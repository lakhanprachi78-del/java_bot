package com.ugrocapital.losbot.chat;

public class OutOfScopeException extends RuntimeException {
    private final String topic;

    public OutOfScopeException(String topic) {
        super("Question is outside the LOS scope: " + topic);
        this.topic = topic;
    }

    public String topic() {
        return topic;
    }
}