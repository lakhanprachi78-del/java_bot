package com.ugrocapital.appstatus.web;

import com.ugrocapital.appstatus.dto.web.FeedbackDetailRequest;
import com.ugrocapital.appstatus.dto.web.FeedbackDetailResponse;
import com.ugrocapital.appstatus.dto.web.FeedbackRequest;
import com.ugrocapital.appstatus.dto.web.FeedbackResponse;
import com.ugrocapital.appstatus.service.FeedbackLogService;
import com.ugrocapital.appstatus.service.QueryLogService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * REST parity port of main.py's /api/feedback and /api/feedback/detail
 * endpoints. Not used by the real Angular frontend (see
 * ChatWebSocketHandler for the WS equivalent) — kept for testing/parity.
 */
@RestController
@RequestMapping("/api")
public class FeedbackController {

    private final QueryLogService queryLogService;
    private final FeedbackLogService feedbackLogService;

    public FeedbackController(QueryLogService queryLogService, FeedbackLogService feedbackLogService) {
        this.queryLogService = queryLogService;
        this.feedbackLogService = feedbackLogService;
    }

    @PostMapping("/feedback")
    public FeedbackResponse feedback(@RequestBody FeedbackRequest payload) {
        Long resolvedId = queryLogService.addFeedback(payload.queryId, payload.positive);
        return new FeedbackResponse(resolvedId != null);
    }

    @PostMapping("/feedback/detail")
    public FeedbackDetailResponse feedbackDetail(@Valid @RequestBody FeedbackDetailRequest payload) {
        List<String> tags = payload.tags.stream().map(String::strip).filter(t -> !t.isEmpty()).toList();
        String comment = (payload.comment == null ? "" : payload.comment).strip();
        if (tags.isEmpty() && comment.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provide at least one reason tag or a comment.");
        }

        Long newId = feedbackLogService.addFeedbackDetail(
                payload.queryId, payload.queryText, payload.answerText, payload.positive,
                tags, comment.isEmpty() ? null : comment);
        return new FeedbackDetailResponse(newId != null, newId);
    }
}
