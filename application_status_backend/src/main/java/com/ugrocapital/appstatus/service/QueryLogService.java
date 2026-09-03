package com.ugrocapital.appstatus.service;

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
 * Local SQLite log of Application Status Bot queries — the anchor table
 * thumbs feedback attaches to. Direct port of query_log.py.
 *
 * Every query is logged as its own row, always — no dedup at log time.
 * Deduplication only ever happens at FEEDBACK time (see addFeedback),
 * and only against another row with the EXACT same normalized question
 * text that has ALREADY received feedback. Lives in its own db file
 * (los_query_log.db), independent of any RAG backend's own query log.
 */
@Service
public class QueryLogService {

    private static final Logger log = LoggerFactory.getLogger(QueryLogService.class);

    private final String jdbcUrl;

    public QueryLogService(AppProperties appProperties) {
        this.jdbcUrl = "jdbc:sqlite:" + appProperties.getQueryLogDbPath();
    }

    @PostConstruct
    public void initDb() {
        try (Connection conn = connect()) {
            try (Statement st = conn.createStatement()) {
                st.execute("""
                        CREATE TABLE IF NOT EXISTS query_log (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            query TEXT NOT NULL,
                            normalized_query TEXT NOT NULL,
                            answer TEXT,
                            source TEXT NOT NULL DEFAULT 'chat',
                            thumbs_up INTEGER NOT NULL DEFAULT 0,
                            thumbs_down INTEGER NOT NULL DEFAULT 0,
                            created_at TEXT NOT NULL,
                            last_seen TEXT NOT NULL
                        )
                        """);
                st.execute("CREATE INDEX IF NOT EXISTS idx_los_query_log_normalized ON query_log (normalized_query)");
                st.execute("CREATE INDEX IF NOT EXISTS idx_los_query_log_created_at ON query_log (created_at)");
            }
        } catch (SQLException e) {
            log.error("LOS QUERY LOG INIT ERROR: {}", e.getMessage());
        }
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(jdbcUrl);
    }

    /** Case/whitespace-insensitive key used ONLY for the feedback-time
     * dedup lookup — never shown to anyone, never used for matching at
     * log time. */
    private String normalize(String text) {
        if (text == null) return "";
        return String.join(" ", text.strip().toLowerCase().split("\\s+"));
    }

    /** Always inserts a new row. `source` is "chat" (free-text/LLM
     * tool-calling) or "direct" (structured menu click). Returns the new
     * row's id, or null on failure. Never throws — a logging failure
     * must never break the actual chat response. */
    public Long logQuery(String query, String answer, String source) {
        try (Connection conn = connect()) {
            String now = Instant.now().toString();
            String sql = "INSERT INTO query_log (query, normalized_query, answer, source, created_at, last_seen) VALUES (?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, query);
                ps.setString(2, normalize(query));
                ps.setString(3, answer);
                ps.setString(4, source);
                ps.setString(5, now);
                ps.setString(6, now);
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        return keys.getLong(1);
                    }
                }
            }
            return null;
        } catch (SQLException e) {
            log.error("LOS QUERY LOG ERROR: {}", e.getMessage());
            return null;
        }
    }

    /** Registers one thumbs vote for rowId. If another row with the
     * EXACT same question text already has feedback on it, the vote is
     * redirected there instead, so repeated feedback on the same
     * recurring question accumulates on one row. Otherwise this row
     * becomes the canonical row going forward. Returns the id the vote
     * actually landed on, or null if rowId doesn't exist or the write
     * failed. Never throws. */
    public Long addFeedback(long rowId, boolean positive) {
        try (Connection conn = connect()) {
            String normalized;
            try (PreparedStatement ps = conn.prepareStatement("SELECT normalized_query FROM query_log WHERE id = ?")) {
                ps.setLong(1, rowId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return null;
                    }
                    normalized = rs.getString(1);
                }
            }

            Long canonicalId = null;
            String canonicalSql = "SELECT id FROM query_log WHERE normalized_query = ? AND id != ? " +
                    "AND (thumbs_up + thumbs_down) > 0 ORDER BY id ASC LIMIT 1";
            try (PreparedStatement ps = conn.prepareStatement(canonicalSql)) {
                ps.setString(1, normalized);
                ps.setLong(2, rowId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        canonicalId = rs.getLong(1);
                    }
                }
            }

            long targetId = canonicalId != null ? canonicalId : rowId;
            String column = positive ? "thumbs_up" : "thumbs_down";
            // `column` is one of exactly two hardcoded literals chosen by
            // this code, never user input — safe to place directly in
            // the SQL text; the actual values below remain bound params.
            String updateSql = "UPDATE query_log SET " + column + " = " + column + " + 1, last_seen = ? WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                ps.setString(1, Instant.now().toString());
                ps.setLong(2, targetId);
                ps.executeUpdate();
            }
            return targetId;
        } catch (SQLException e) {
            log.error("LOS QUERY LOG FEEDBACK ERROR: {}", e.getMessage());
            return null;
        }
    }

    /** Reads rows back as plain maps, most-recent first. */
    public List<Map<String, Object>> fetchAll(Integer limit) {
        String sql = "SELECT * FROM query_log ORDER BY created_at DESC" + (limit != null ? " LIMIT " + limit : "");
        List<Map<String, Object>> out = new ArrayList<>();
        try (Connection conn = connect(); Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            ResultSetMetaData meta = rs.getMetaData();
            int cols = meta.getColumnCount();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= cols; i++) {
                    row.put(meta.getColumnName(i), rs.getObject(i));
                }
                out.add(row);
            }
        } catch (SQLException e) {
            log.error("LOS QUERY LOG FETCH ERROR: {}", e.getMessage());
        }
        return out;
    }
}
