package com.ugrocapital.appstatus.service;

/**
 * Raised when the model calls the not_in_scope tool — i.e. it has
 * decided the current message isn't a loan-application data/status
 * question. This is a signal to the caller (ChatController) to hand the
 * message off to the SkaleUp RAG backend, not a real error. The message
 * carries the clean, isolated out-of-scope topic (the tool's "topic"
 * argument). Mirrors chat_engine.py's OutOfScopeError.
 */
public class OutOfScopeException extends RuntimeException {
    public OutOfScopeException(String topic) {
        super(topic);
    }
}
