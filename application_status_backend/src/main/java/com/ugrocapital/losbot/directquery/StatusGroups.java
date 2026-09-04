package com.ugrocapital.losbot.directquery;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The `application.statuscode` column has messy real-world data — different
 * casing, hyphens vs spaces, and even a typo ("CREDIT ASSISMENT"). The
 * frontend, however, only wants to show 4 clean branches:
 *
 *   Sales            -> Pre-Login, Pre-Login Review, Pre-Login Discrepant, Sales Discrepant
 *   Credit Assessment
 *   Pre-Disbursement
 *   Disbursement     -> Sent to LMS, Sent to LMS Failed
 *
 * This class is the single place that maps a "branch" onto every raw
 * statuscode spelling that belongs to it, so a search for one branch
 * returns rows regardless of which spelling was used when the row was
 * created.
 *
 * Two things can be resolved here:
 *  - GROUP_KEY: the exact value the status menu buttons in the Angular app
 *    send (see frontend chat.models.ts STATUS_CATEGORIES), e.g. "sales_pre_login".
 *  - ALIAS: a plain-English phrase, used when the LLM free-text chat picks
 *    a status itself (see ToolSchemas.STATUS_CODES / search_by_status),
 *    e.g. "pre login", "sales discrepant".
 *
 * Anything that isn't a known branch (REJECTED, APPROVED, REFER, CLOSED,
 * etc.) is left alone and falls back to the old exact-match search.
 */
public final class StatusGroups {

    private record Group(List<String> statusCodes) {
    }

    private static final Map<String, Group> GROUP_KEY_TO_GROUP = new LinkedHashMap<>();
    private static final Map<String, Group> ALIAS_TO_GROUP = new LinkedHashMap<>();

    static {
        // --- 1. Sales ---
        register("sales_pre_login", List.of("pre login"),
                "PRE-LOGIN", "Pre-login");
        register("sales_pre_login_review", List.of("pre login review"),
                "PRE-LOGIN REVIEW", "PRE LOGIN REVIEW");
        register("sales_pre_login_discrepant", List.of("pre login discrepant"),
                "PRE-LOGIN DISCREPANT");
        register("sales_discrepant", List.of("sales discrepant"),
                "SALES-DISCREPANT");

        // --- 2. Credit Assessment ---
        register("credit_assessment", List.of("credit assessment", "credit assisment"),
                "CREDIT ASSESSMENT", "CREDIT ASSISMENT");

        // --- 3. Pre-Disbursement ---
        register("pre_disbursement", List.of("pre disbursement"),
                "PRE-DISBURSEMENT");

        // --- 4. Disbursement ---
        register("disbursement_sent_to_lms", List.of("sent to lms"),
                "SENT TO LMS");
        register("disbursement_sent_to_lms_failed", List.of("sent to lms failed"),
                "SENT TO LMS FAILED");
    }

    private static void register(String groupKey, List<String> aliases, String... rawStatusCodes) {
        Group group = new Group(List.of(rawStatusCodes));
        GROUP_KEY_TO_GROUP.put(groupKey, group);
        for (String alias : aliases) {
            ALIAS_TO_GROUP.put(normalize(alias), group);
        }
    }

    /**
     * Resolves a button value (e.g. "sales_pre_login") or a free-text phrase
     * (e.g. "pre login") to every raw statuscode spelling in that branch.
     * Returns null if the value doesn't match a known branch — the caller
     * should then fall back to an exact match on the raw value.
     */
    public static List<String> resolve(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();

        Group byKey = GROUP_KEY_TO_GROUP.get(trimmed.toLowerCase(Locale.ROOT));
        if (byKey != null) {
            return byKey.statusCodes();
        }

        Group byAlias = ALIAS_TO_GROUP.get(normalize(trimmed));
        return byAlias == null ? null : byAlias.statusCodes();
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }

    private StatusGroups() {
    }
}