package com.ugrocapital.losbot.log;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class FeedbackLogService {

    private static final Logger log = LoggerFactory.getLogger(FeedbackLogService.class);
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public FeedbackLogService(@Qualifier("feedbackLogJdbcTemplate") JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void initialize() {
        runSilently("CREATE TABLE IF NOT EXISTS feedback_log ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, query_id INTEGER, query_text TEXT, answer_text TEXT,"
                + "positive INTEGER NOT NULL DEFAULT 0, tags TEXT NOT NULL DEFAULT '[]', comment TEXT, created_at TEXT NOT NULL)");
        runSilently("CREATE INDEX IF NOT EXISTS idx_los_feedback_log_query_id ON feedback_log (query_id)");
        runSilently("CREATE INDEX IF NOT EXISTS idx_los_feedback_log_created_at ON feedback_log (created_at)");
    }

    public Long addFeedbackDetail(Integer queryId, String queryText, String answerText,
            boolean positive, List<String> tags, String comment) {
        try {
            String tagsJson = objectMapper.writeValueAsString(tags == null ? List.of() : tags);
            jdbc.update("INSERT INTO feedback_log (query_id, query_text, answer_text, positive, tags, comment, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    queryId, queryText, answerText, positive ? 1 : 0, tagsJson, comment, now());
            return jdbc.queryForObject("SELECT last_insert_rowid()", Long.class);
        } catch (JsonProcessingException exception) {
            log.warn("Feedback tag serialization failed", exception);
            return null;
        } catch (Exception exception) {
            log.warn("Feedback detail logging failed", exception);
            return null;
        }
    }

    public List<Map<String, Object>> fetchAll() {
        try {
            return jdbc.queryForList("SELECT * FROM feedback_log ORDER BY id");
        } catch (Exception exception) {
            log.warn("Feedback log read failed", exception);
            return List.of();
        }
    }

    private void runSilently(String sql) {
        try {
            jdbc.execute(sql);
        } catch (Exception exception) {
            log.warn("Feedback log database initialization failed", exception);
        }
    }

    private String now() {
        return OffsetDateTime.now(ZoneOffset.UTC).toString();
    }
}