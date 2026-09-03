package com.ugrocapital.appstatus.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ugrocapital.appstatus.config.AppProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Local SQLite log of DETAILED user feedback (predefined reason tags +
 * free-text comments). Separate db file from QueryLogService's
 * query_log.db on purpose — this is a plain, append-only, one-row-per-
 * submission event log. Direct port of feedback_log.PY.
 *
 * Nothing is written unless at least one of tags/comment is non-empty —
 * enforced by the caller (ChatController/WebSocket handler), same as
 * the Python version's main.py.
 */
@Service
public class FeedbackLogService {

    private static final Logger log = LoggerFactory.getLogger(FeedbackLogService.class);
    private final ObjectMapper objectMapper;

    private final String jdbcUrl;

    public FeedbackLogService(AppProperties appProperties, ObjectMapper objectMapper) {
        this.jdbcUrl = "jdbc:sqlite:" + appProperties.getFeedbackLogDbPath();
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void initDb() {
        try (Connection conn = connect()) {
            try (Statement st = conn.createStatement()) {
                st.execute("""
                        CREATE TABLE IF NOT EXISTS feedback_log (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            query_id INTEGER,
                            query_text TEXT,
                            answer_text TEXT,
                            positive INTEGER NOT NULL DEFAULT 0,
                            tags TEXT NOT NULL DEFAULT '[]',
                            comment TEXT,
                            created_at TEXT NOT NULL
                        )
                        """);
                st.execute("CREATE INDEX IF NOT EXISTS idx_los_feedback_log_query_id ON feedback_log (query_id)");
                st.execute("CREATE INDEX IF NOT EXISTS idx_los_feedback_log_created_at ON feedback_log (created_at)");
            }
        } catch (SQLException e) {
            log.error("LOS FEEDBACK LOG INIT ERROR: {}", e.getMessage());
        }
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(jdbcUrl);
    }

    /** Inserts one feedback submission row. Returns the new row id, or
     * null if the write failed. Never throws — a feedback-logging hiccup
     * must never surface as an error to the person just trying to send
     * a comment. */
    public Long addFeedbackDetail(Long queryId, String queryText, String answerText,
                                   boolean positive, List<String> tags, String comment) {
        try (Connection conn = connect()) {
            List<String> cleanTags = new ArrayList<>();
            if (tags != null) {
                for (String t : tags) {
                    if (t != null && !t.strip().isEmpty()) {
                        cleanTags.add(t.strip());
                    }
                }
            }
            String cleanComment = (comment == null || comment.strip().isEmpty()) ? null : comment.strip();
            String now = Instant.now().toString();
            String tagsJson = objectMapper.writeValueAsString(cleanTags);

            String sql = "INSERT INTO feedback_log (query_id, query_text, answer_text, positive, tags, comment, created_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                if (queryId != null) ps.setLong(1, queryId); else ps.setNull(1, Types.INTEGER);
                ps.setString(2, queryText);
                ps.setString(3, answerText);
                ps.setInt(4, positive ? 1 : 0);
                ps.setString(5, tagsJson);
                ps.setString(6, cleanComment);
                ps.setString(7, now);
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        return keys.getLong(1);
                    }
                }
            }
            return null;
        } catch (Exception e) {
            log.error("LOS FEEDBACK LOG ERROR: {}", e.getMessage());
            return null;
        }
    }

    /** Reads rows back as plain maps, tags pre-parsed into a List,
     * most-recent submission first. */
    public List<Map<String, Object>> fetchAll(Integer limit) {
        String sql = "SELECT * FROM feedback_log ORDER BY created_at DESC" + (limit != null ? " LIMIT " + limit : "");
        List<Map<String, Object>> out = new ArrayList<>();
        try (Connection conn = connect(); Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            ResultSetMetaData meta = rs.getMetaData();
            int cols = meta.getColumnCount();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= cols; i++) {
                    row.put(meta.getColumnName(i), rs.getObject(i));
                }
                try {
                    row.put("tags", objectMapper.readValue((String) row.get("tags"), List.class));
                } catch (Exception e) {
                    row.put("tags", List.of());
                }
                row.put("positive", Integer.valueOf(1).equals(row.get("positive")));
                out.add(row);
            }
        } catch (SQLException e) {
            log.error("LOS FEEDBACK LOG FETCH ERROR: {}", e.getMessage());
        }
        return out;
    }
}
