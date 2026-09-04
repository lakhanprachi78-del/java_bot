package com.ugrocapital.losbot.repository;

import java.util.List;

public record SearchResult(
        List<ApplicationDto> results,
        long totalMatches,
        int returned,
        boolean hasMore) {
}