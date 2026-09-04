package com.ugrocapital.losbot.auth;

public class UnknownSessionException extends RuntimeException {
    public UnknownSessionException(String sessionId) {
        super("Unknown session: " + sessionId);
    }
}