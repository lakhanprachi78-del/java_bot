package com.ugrocapital.appstatus.auth;

/**
 * Mirrors auth.py's UnknownSessionError. Callers must treat this as
 * "reject the request", never "fall back to a default user" — that would
 * be an authorization bypass.
 */
public class UnknownSessionException extends RuntimeException {
    public UnknownSessionException(String message) {
        super(message);
    }
}
