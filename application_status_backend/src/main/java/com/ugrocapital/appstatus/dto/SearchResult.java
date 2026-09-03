package com.ugrocapital.appstatus.dto;

import java.util.List;

/**
 * Mirrors the dict shape every repository.py search_* function returns:
 * {"results", "total_matches", "returned", "has_more"}.
 */
public record SearchResult(
        List<ApplicationDto> results,
        long totalMatches,
        int returned,
        boolean hasMore
) {
}
