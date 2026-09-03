package com.ugrocapital.appstatus.dto.web;

public class FeedbackDetailResponse {
    public boolean success;
    public Long id;

    public FeedbackDetailResponse() {
    }

    public FeedbackDetailResponse(boolean success, Long id) {
        this.success = success;
        this.id = id;
    }
}
