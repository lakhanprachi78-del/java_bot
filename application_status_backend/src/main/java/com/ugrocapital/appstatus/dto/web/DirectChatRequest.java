package com.ugrocapital.appstatus.dto.web;

import jakarta.validation.constraints.NotBlank;

/**
 * For the frontend's structured menu inputs ONLY — status picker,
 * application ID field, applicant name field, date field — where the UI
 * already knows exactly which single field+value the user submitted.
 * See DirectQueryService for why this skips the LLM entirely.
 *
 * Do NOT use this for free text: that belongs on the "chat" flow
 * instead, same as before.
 */
public class DirectChatRequest {
    @NotBlank
    public String sessionId;

    @NotBlank
    public String field; // "application_id" | "status" | "applicant_name" | "applicant_email" | "date_time"

    @NotBlank
    public String value;

    public int offset = 0;
}
