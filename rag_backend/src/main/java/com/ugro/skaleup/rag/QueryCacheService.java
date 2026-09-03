package com.ugro.skaleup.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Service
public class QueryCacheService {

    private static final Logger log = LoggerFactory.getLogger(QueryCacheService.class);
    private static final int POSITIVE_WEIGHT = 3;
    private static final int NEGATIVE_WEIGHT = 5;

    private static final class CacheEntry {
        final int id;
        final String question;
        final float[] vector;
        final ApiModels.RagChatResponse response;
        int hitCount;
        int positive;
        int negative;

        CacheEntry(int id, String question, float[] vector, ApiModels.RagChatResponse response,
                   int hitCount, int positive, int negative) {
            this.id = id;
            this.question = question;
            this.vector = vector;
            this.response = response;
            this.hitCount = hitCount;
            this.positive = positive;
            this.negative = negative;
        }

        int score() {
            return hitCount + positive * POSITIVE_WEIGHT - negative * NEGATIVE_WEIGHT;
        }
    }

    private final EmbeddingService embeddings;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final double threshold;
    private final int maxSize;
    private final Map<Integer, CacheEntry> entries = new LinkedHashMap<>();
    private final AtomicInteger idGen = new AtomicInteger(1);
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public QueryCacheService(
            EmbeddingService embeddings,
            JdbcTemplate jdbc,
            ObjectMapper mapper,
            @Value("${RAG_CACHE_SIMILARITY_THRESHOLD:0.93}") double threshold,
            @Value("${RAG_CACHE_MAX_SIZE:500}") int maxSize) {
        this.embeddings = embeddings;
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.threshold = threshold;
        this.maxSize = maxSize;
        init();
    }

    private void init() {
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS query_cache (
                id INTEGER PRIMARY KEY,
                question TEXT NOT NULL,
                vector BLOB NOT NULL,
                answer TEXT NOT NULL,
                sources TEXT NOT NULL,
                hit_count INTEGER NOT NULL DEFAULT 1,
                positive INTEGER NOT NULL DEFAULT 0,
                negative INTEGER NOT NULL DEFAULT 0
            )
            """);

        RowMapper<CacheEntry> mapRow = (rs, i) -> {
            int id = rs.getInt("id");
            String question = rs.getString("question");
            float[] vector = toFloatArray(rs.getBytes("vector"));
            String answer = rs.getString("answer");
            List<ApiModels.ChatSource> sources;
            try {
                sources = mapper.readValue(rs.getString("sources"),
                        mapper.getTypeFactory().constructCollectionType(List.class, ApiModels.ChatSource.class));
            } catch (Exception e) {
                sources = List.of();
            }
            ApiModels.RagChatResponse response = new ApiModels.RagChatResponse(answer, sources, id);
            return new CacheEntry(id, question, vector, response,
                    rs.getInt("hit_count"), rs.getInt("positive"), rs.getInt("negative"));
        };

        List<CacheEntry> loaded = jdbc.query("SELECT * FROM query_cache", mapRow);
        int maxId = 0;
        for (CacheEntry e : loaded) {
            entries.put(e.id, e);
            maxId = Math.max(maxId, e.id);
        }
        idGen.set(maxId + 1);
        log.info("Loaded {} cached Q&A entries from SQLite", loaded.size());
    }

    public Optional<ApiModels.RagChatResponse> lookup(String question) throws Exception {
        float[] v = embeddings.embedOne(question);

        CacheEntry best = null;
        float bestScore = -1f;

        lock.writeLock().lock();
        try {
            for (CacheEntry e : entries.values()) {
                float score = cosine(v, e.vector);
                if (score > bestScore) {
                    bestScore = score;
                    best = e;
                }
            }

            if (best != null && bestScore >= threshold) {
                best.hitCount++;
                jdbc.update("UPDATE query_cache SET hit_count = ? WHERE id = ?", best.hitCount, best.id);
                log.info("Cache HIT (score {}, hits {}, +{}/-{}) — \"{}\" matched cached \"{}\"",
                        String.format("%.3f", bestScore), best.hitCount, best.positive, best.negative, question, best.question);
                return Optional.of(best.response);
            }
        } finally {
            lock.writeLock().unlock();
        }

        log.info("Cache MISS (best score {})", best == null ? "n/a" : String.format("%.3f", bestScore));
        return Optional.empty();
    }

    public ApiModels.RagChatResponse put(String question, String answer, List<ApiModels.ChatSource> sources) throws Exception {
        float[] v = embeddings.embedOne(question);
        int id = idGen.getAndIncrement();
        ApiModels.RagChatResponse response = new ApiModels.RagChatResponse(answer, sources, id);
        String sourcesJson = mapper.writeValueAsString(sources);

        lock.writeLock().lock();
        try {
            entries.put(id, new CacheEntry(id, question, v, response, 1, 0, 0));
            jdbc.update("INSERT INTO query_cache (id, question, vector, answer, sources, hit_count, positive, negative) VALUES (?,?,?,?,?,1,0,0)",
                    id, question, toBytes(v), answer, sourcesJson);
            evictIfNeeded();
        } finally {
            lock.writeLock().unlock();
        }

        return response;
    }

    public boolean recordFeedback(Integer queryId, boolean positive) {
        if (queryId == null) return false;

        lock.writeLock().lock();
        try {
            CacheEntry e = entries.get(queryId);
            if (e == null) return false;

            if (positive) {
                e.positive++;
                jdbc.update("UPDATE query_cache SET positive = ? WHERE id = ?", e.positive, e.id);
                log.info("Feedback +1 positive on cached query {} (\"{}\") — now +{}/-{}", e.id, e.question, e.positive, e.negative);
            } else {
                e.negative++;
                log.info("Feedback +1 negative on cached query {} (\"{}\") — evicting from cache", e.id, e.question);
                entries.remove(queryId);
                jdbc.update("DELETE FROM query_cache WHERE id = ?", queryId);
            }
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void clear() {
        lock.writeLock().lock();
        try {
            entries.clear();
            jdbc.update("DELETE FROM query_cache");
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void evictIfNeeded() {
        while (entries.size() > maxSize) {
            CacheEntry worst = null;
            for (CacheEntry e : entries.values()) {
                if (worst == null || e.score() < worst.score()
                        || (e.score() == worst.score() && e.id < worst.id)) {
                    worst = e;
                }
            }
            if (worst == null) break;
            log.info("Cache full — evicting lowest-value entry {} (\"{}\", score {})", worst.id, worst.question, worst.score());
            entries.remove(worst.id);
            jdbc.update("DELETE FROM query_cache WHERE id = ?", worst.id);
        }
    }

    private float cosine(float[] a, float[] b) {
        int n = Math.min(a.length, b.length);
        double s = 0;
        for (int i = 0; i < n; i++) s += a[i] * b[i];
        return (float) s;
    }

    private byte[] toBytes(float[] v) {
        ByteBuffer buf = ByteBuffer.allocate(v.length * 4);
        for (float f : v) buf.putFloat(f);
        return buf.array();
    }

    private float[] toFloatArray(byte[] b) {
        ByteBuffer buf = ByteBuffer.wrap(b);
        float[] v = new float[b.length / 4];
        for (int i = 0; i < v.length; i++) v[i] = buf.getFloat();
        return v;
    }
}