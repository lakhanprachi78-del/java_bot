package com.ugrocapital.appstatus.repository;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * `remark` is free text a staff member could have typed anything into,
 * including a PAN, password, or account number that never should have
 * been entered there. This is a structural backstop applied at the data
 * layer — it runs on every row before the text ever reaches the LLM, so
 * it doesn't depend on the LLM (or the prompt) behaving correctly.
 * Direct port of repository.py's _redact_sensitive.
 */
@Component
public class SensitiveDataRedactor {

    private static final Pattern PAN_RE = Pattern.compile("\\b[A-Z]{5}[0-9]{4}[A-Z]\\b"); // Indian PAN format
    private static final Pattern PASSWORD_RE = Pattern.compile(
            "(?i)\\b(password|passwd|pwd|otp|pin)\\b\\s*[:=\\-]?\\s*\\S+");
    private static final Pattern LONG_DIGIT_RE = Pattern.compile("\\b\\d{9,}\\b"); // account/card-like numbers

    // remarks is free text with no length cap in the schema. A long
    // remark gets sent in full for every record in every batch (up to
    // MAX_ROWS_RETURNED of them), even when nobody asked about it — pure
    // token cost. Cap it here, at the data layer, so it's capped no
    // matter which caller reads it (LLM path or the direct/non-LLM path).
    private static final int REMARK_MAX_CHARS = 500;

    public String redact(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String result = PAN_RE.matcher(text).replaceAll("[REDACTED-PAN]");
        result = PASSWORD_RE.matcher(result).replaceAll("[REDACTED-CREDENTIAL]");
        result = LONG_DIGIT_RE.matcher(result).replaceAll("[REDACTED-NUMBER]");
        if (result.length() > REMARK_MAX_CHARS) {
            result = result.substring(0, REMARK_MAX_CHARS) + "... [truncated]";
        }
        return result;
    }
}
