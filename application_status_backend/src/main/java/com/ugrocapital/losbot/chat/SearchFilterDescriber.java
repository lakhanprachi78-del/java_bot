package com.ugrocapital.losbot.chat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class SearchFilterDescriber {
    private SearchFilterDescriber() {
    }

    public static String describeSearchFilters(Collection<String> filters) {
        if (filters == null || filters.isEmpty()) {
            return "your search";
        }
        return String.join(", ", filters);
    }

    public static String narrowHint(Collection<String> usedFilters) {
        List<String> available = new ArrayList<>(List.of("application ID", "status", "applicant name", "applicant email", "date"));
        if (usedFilters != null) {
            usedFilters.stream().map(String::toLowerCase).forEach(used -> available.removeIf(candidate -> candidate.toLowerCase().contains(used)));
        }
        if (available.isEmpty()) {
            return "Use the offset to see the next results.";
        }
        return "Try narrowing the search by " + String.join(", ", available) + ".";
    }
}