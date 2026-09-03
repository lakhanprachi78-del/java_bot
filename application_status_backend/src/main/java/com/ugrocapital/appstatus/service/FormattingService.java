package com.ugrocapital.appstatus.service;

import com.ugrocapital.appstatus.dto.ApplicationDto;
import com.ugrocapital.appstatus.dto.SearchResult;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Function;

/**
 * Deterministic, LLM-free reply formatting for search/lookup results.
 * Direct port of formatting.py — see that file's docstring for why this
 * exists: the reply shape is a rigid template, so there's no actual
 * language generation happening; this reproduces it in code instead of
 * paying an LLM to re-type structured data as prose.
 *
 * Used by both DirectQueryService (no-LLM path) and ChatEngine's
 * fast-path for single search/lookup tool calls.
 */
@Service
public class FormattingService {

    private record Field(String label, Function<ApplicationDto, String> getter, boolean upperCase) {
    }

    // (label, getter) in the exact required order. "Created On" / "Last
    // Updated On" always come last, in this order — never re-sort this
    // list. Note "Created By" is intentionally absent — matches
    // formatting.py's _FIELD_ORDER exactly, which never displays it
    // either even though the dto carries it.
    private static final List<Field> FIELD_ORDER = List.of(
            new Field("Application ID", d -> d.applicationId, false),
            new Field("Applicant Name", d -> d.applicantName, false),
            new Field("Applicant Email", d -> d.applicantEmail, false),
            new Field("Co-Applicant Name", d -> d.coApplicantName, false),
            new Field("Application Status", d -> d.status, true),
            new Field("Last Updated By", d -> d.lastUpdatedBy, false),
            new Field("Created On", d -> d.createdAt, false),
            new Field("Last Updated On", d -> d.lastUpdatedAt, false)
    );

    /** One application -> one field block. Missing/null/blank fields are
     * omitted entirely (never shown blank or as "null"). Status is
     * upper-cased for display; the underlying data is untouched. */
    public String formatApplicationBlock(ApplicationDto app) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Field f : FIELD_ORDER) {
            String value = f.getter().apply(app);
            if (value == null || value.isBlank()) {
                continue;
            }
            if (f.upperCase()) {
                value = value.toUpperCase();
            }
            if (!first) sb.append("\n");
            sb.append(f.label()).append(": ").append(value);
            first = false;
        }
        return sb.toString();
    }

    /** Multiple applications -> blocks separated by one blank line each.
     * No numbering, no bullets, no table. */
    public String formatApplicationBlocks(List<ApplicationDto> apps) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < apps.size(); i++) {
            if (i > 0) sb.append("\n\n");
            sb.append(formatApplicationBlock(apps.get(i)));
        }
        return sb.toString();
    }

    /**
     * Full search reply (count line + blocks + pagination note).
     *
     * @param filterDescription human phrase for the count line, e.g.
     *                          "with status pre-login" or "created on 16 Aug 2026".
     * @param offset            the offset this call was made with — page 0 gets
     *                          the opening count line, later pages don't.
     * @param narrowHint        what to suggest adding if there's more than one page
     *                          — pass null to omit the suggestion line entirely.
     */
    public String formatSearchReply(SearchResult result, String filterDescription, int offset, String narrowHint) {
        long total = result.totalMatches();
        boolean hasMore = result.hasMore();
        List<ApplicationDto> apps = result.results();

        List<String> lines = new java.util.ArrayList<>();

        if (offset == 0) {
            if (total == 0) {
                return "There are no applications " + filterDescription + ".";
            }
            if (hasMore) {
                lines.add("There are " + total + " applications " + filterDescription + " — showing the 5 most recent.");
            } else {
                lines.add("There are " + total + " applications " + filterDescription + ".");
            }
        }

        if (!apps.isEmpty()) {
            if (!lines.isEmpty()) lines.add(""); // blank line between count line and blocks
            lines.add(formatApplicationBlocks(apps));
        }

        if (hasMore && narrowHint != null) {
            lines.add("");
            lines.add("There are more matches — add " + narrowHint + " to narrow it down, or ask for more results.");
        } else if (hasMore) {
            lines.add("");
            lines.add("There are more matches — ask for more results to see the next batch.");
        }

        return String.join("\n", lines);
    }

    /** For an exact single-record lookup (e.g. by application ID) — no
     * count line, just the block, or a plain not-found message. */
    public String formatSingleLookupReply(ApplicationDto app) {
        if (app == null) {
            return "No application found with that ID.";
        }
        return formatApplicationBlock(app);
    }
}
