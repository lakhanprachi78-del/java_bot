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

@Service
public class QueryLogService {

    private static final Logger log = LoggerFactory.getLogger(QueryLogService.class);
    private final JdbcTemplate jdbc;

    public QueryLogService(@Qualifier("queryLogJdbcTemplate") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    public void initialize() {
        runSilently("CREATE TABLE IF NOT EXISTS query_log ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, query TEXT NOT NULL, normalized_query TEXT NOT NULL,"
                + "answer TEXT, source TEXT NOT NULL DEFAULT 'chat', thumbs_up INTEGER NOT NULL DEFAULT 0,"
                + "thumbs_down INTEGER NOT NULL DEFAULT 0, created_at TEXT NOT NULL, last_seen TEXT NOT NULL)");
        runSilently("CREATE INDEX IF NOT EXISTS idx_los_query_log_normalized ON query_log (normalized_query)");
        runSilently("CREATE INDEX IF NOT EXISTS idx_los_query_log_created_at ON query_log (created_at)");
    }

    public Integer logQuery(String query, String answer, String source) {
        try {
            String now = now();
            jdbc.update("INSERT INTO query_log (query, normalized_query, answer, source, created_at, last_seen) VALUES (?, ?, ?, ?, ?, ?)",
                    query, normalize(query), answer, source == null ? "chat" : source, now, now);
            return jdbc.queryForObject("SELECT last_insert_rowid()", Integer.class);
        } catch (Exception exception) {
            log.warn("Query logging failed", exception);
            return null;
        }
    }

    public void addFeedback(int queryId, boolean positive) {
        try {
            Map<String, Object> query = jdbc.queryForMap("SELECT normalized_query FROM query_log WHERE id = ?", queryId);
            List<Integer> canonical = jdbc.query("SELECT id FROM query_log WHERE normalized_query = ? AND (thumbs_up > 0 OR thumbs_down > 0) ORDER BY id LIMIT 1",
                    (result, row) -> result.getInt("id"), query.get("normalized_query"));
            int targetId = canonical.isEmpty() ? queryId : canonical.get(0);
            String column = positive ? "thumbs_up" : "thumbs_down";
            jdbc.update("UPDATE query_log SET " + column + " = " + column + " + 1, last_seen = ? WHERE id = ?", now(), targetId);
        } catch (Exception exception) {
            log.warn("Query feedback logging failed", exception);
        }
    }

    public List<Map<String, Object>> fetchAll() {
        try {
            return jdbc.queryForList("SELECT * FROM query_log ORDER BY id");
        } catch (Exception exception) {
            log.warn("Query log read failed", exception);
            return List.of();
        }
    }

    public String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase();
    }

    private void runSilently(String sql) {
        try {
            jdbc.execute(sql);
        } catch (Exception exception) {
            log.warn("Query log database initialization failed", exception);
        }
    }

    private String now() {
        return OffsetDateTime.now(ZoneOffset.UTC).toString();
    }
}