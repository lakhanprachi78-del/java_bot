package com.ugrocapital.appstatus.dto.web;

import jakarta.validation.constraints.NotBlank;

public class ChatRequest {

    @NotBlank
    public String sessionId;

    @NotBlank
    public String message;

    // True only when THIS request is itself the result of a handoff from
    // the SkaleUp bot (it decided the question wasn't a general SkaleUp
    // question). Never set this from the frontend. It exists purely to
    // stop routing ping-pong: if we're not_in_scope on a message that's
    // already a handoff, we answer with the plain fallback instead of
    // forwarding again.
    public boolean isHandoff = false;
}
