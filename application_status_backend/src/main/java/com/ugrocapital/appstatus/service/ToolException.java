package com.ugrocapital.appstatus.service;

/** Mirrors tools.py's ToolError. */
public class ToolException extends RuntimeException {
    public ToolException(String message) {
        super(message);
    }
}
