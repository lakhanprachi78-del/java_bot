package com.ugrocapital.appstatus.dto.web;

import jakarta.validation.constraints.Size;

import java.util.ArrayList;
import java.util.List;

public class FeedbackDetailRequest {
    // Nullable: a query_id can be missing if the original chat turn never
    // got one logged (e.g. a logging hiccup at answer time), but the
    // person's typed comment/tags are still worth keeping either way.
    public Long queryId;

    // Original question text, so los_feedback_log.db is readable
    // standalone without joining back to los_query_log.db.
    public String queryText;

    // The exact answer text the person was looking at when they gave
    // this feedback, so re-reading a row later tells the whole story.
    public String answerText;

    public boolean positive;

    public List<String> tags = new ArrayList<>();

    @Size(max = 2000)
    public String comment;
}
