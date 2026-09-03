package com.ugrocapital.appstatus.service;

/**
 * User-facing message for a bad direct-query request (bad field name,
 * unparseable date, etc). Callers return this as the reply text, same as
 * a validation failure — never a 500. Mirrors direct_queries.py's
 * DirectQueryError.
 */
public class DirectQueryException extends RuntimeException {
    public DirectQueryException(String message) {
        super(message);
    }
}
