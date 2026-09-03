package com.ugrocapital.appstatus.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * This is the ENTIRE interface the LLM has to your data. It can pick a
 * tool name and supply arguments matching the schema below — nothing
 * else. Direct port of tools.py's STATUS_CODES + TOOL_SCHEMAS.
 *
 * CRITICAL: "who is asking" (AuthContext) is NEVER one of the tool
 * parameters the LLM fills in — see ToolDispatcher.dispatch(), which
 * takes auth as a separate server-resolved argument, never read from the
 * LLM's arguments map. Keeping auth out of the tool schema makes prompt
 * injection targeting authorization structurally impossible, not just
 * discouraged.
 */
public final class ToolSchemas {

    private ToolSchemas() {
    }

    // Must match ChatEngine's SYSTEM_PROMPT exactly, and both must match
    // the real values in the database.
    public static final List<String> STATUS_CODES = List.of(
            "pre-login",
            "pre-login review",
            "pre login discrepant",
            "sales discrepant",
            "credit assessment",
            "pre-disbursement",
            "approved",
            "rejected",
            "sent to lms"
    );

    private static Map<String, Object> stringProp(String description) {
        return Map.of("type", "string", "description", description);
    }

    private static Map<String, Object> intProp(String description) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "integer");
        m.put("description", description);
        return m;
    }

    private static Map<String, Object> function(String name, String description, Map<String, Object> parameters) {
        Map<String, Object> fn = new LinkedHashMap<>();
        fn.put("name", name);
        fn.put("description", description);
        fn.put("parameters", parameters);
        Map<String, Object> wrapper = new LinkedHashMap<>();
        wrapper.put("type", "function");
        wrapper.put("function", fn);
        return wrapper;
    }

    private static Map<String, Object> objectParams(Map<String, Object> properties, List<String> required) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "object");
        m.put("properties", properties);
        m.put("required", required);
        return m;
    }

    public static final List<Map<String, Object>> TOOL_SCHEMAS = List.of(

            function("get_application_by_id",
                    "Look up a single loan application's full status and details using its exact application ID. Only returns it if the current user created it or currently owns it.",
                    objectParams(
                            Map.of("application_id", stringProp("The application ID, e.g. APP-00012")),
                            List.of("application_id")
                    )),

            function("search_by_created_by",
                    "Find loan applications by the name of the person who created them (partial match allowed). Results are automatically limited to applications the current user is allowed to see.",
                    objectParams(
                            new LinkedHashMap<>(Map.of(
                                    "name", stringProp("Full or partial name to search for"),
                                    "offset", intProp("Number of matching records to skip, for pagination. Use this when the user asks for more results with the same filter. Defaults to 0.")
                            )),
                            List.of("name")
                    )),

            function("search_by_applicant_name",
                    "Find loan applications by the applicant's name (partial match allowed). Results are automatically limited to applications the current user is allowed to see.",
                    objectParams(
                            Map.of("name", stringProp("Full or partial applicant name to search for")),
                            List.of("name")
                    )),

            function("search_by_applicant_email",
                    "Find loan applications by an applicant's email address (partial match allowed). Results are automatically limited to applications the current user is allowed to see.",
                    objectParams(
                            Map.of("email", stringProp("Full or partial applicant email address to search for")),
                            List.of("email")
                    )),

            function("search_by_date",
                    "Find loan applications created on one exact calendar date, limited to what the current user is allowed to see.",
                    objectParams(
                            new LinkedHashMap<>(Map.of(
                                    "date", stringProp("Date in YYYY-MM-DD format"),
                                    "offset", intProp("Number of matching records to skip, for pagination. Use this when the user asks for more results with the same date. Defaults to 0.")
                            )),
                            List.of("date")
                    )),

            function("search_by_date_range",
                    "Find loan applications created between two dates (inclusive), limited to what the current user is allowed to see. Use for phrases like 'this week', 'last month' — resolve to concrete dates yourself first.",
                    objectParams(
                            new LinkedHashMap<>(Map.of(
                                    "start_date", stringProp("YYYY-MM-DD"),
                                    "end_date", stringProp("YYYY-MM-DD"),
                                    "offset", intProp("Number of matching records to skip, for pagination. Use this when the user asks for more results with the same date range. Defaults to 0.")
                            )),
                            List.of("start_date", "end_date")
                    )),

            function("search_by_status",
                    "Find loan applications with a specific status code, limited to what the current user is allowed to see.",
                    objectParams(
                            new LinkedHashMap<>(Map.of(
                                    "status_code", statusCodeProp(),
                                    "offset", intProp("Number of matching records to skip, for pagination. Use this when the user asks for more results with the same status. Defaults to 0.")
                            )),
                            List.of("status_code")
                    )),

            function("not_in_scope",
                    "Call this INSTEAD of answering or refusing in prose whenever the user's question is not about loan application data/status/search (e.g. general SkaleUp product questions, onboarding steps, the sales journey, or any other non-application topic). Do not call this for technical-error reports — those are handled directly per the TECHNICAL SUPPORT instructions instead. " +
                            "You MUST pass a 'topic' argument containing ONLY the user's actual out-of-scope question, in their own words — strip out any application-search phrasing that happened to be bundled into the same message (e.g. an active status filter, 'show applications with status: X and ...', a date range, an application ID, etc.). This isolated topic is handed off to a separate SkaleUp knowledge-base bot, so it must read as a clean, standalone question — not a fragment of a larger sentence.",
                    objectParams(
                            Map.of("topic", stringProp("The user's out-of-scope question, cleaned of any application-search context. E.g. if the full message was 'Show applications with status: sent to lms and What is LAP', this should just be 'What is LAP', not the whole message.")),
                            List.of("topic")
                    )),

            function("combined_search",
                    "Search using any combination of creator name, applicant name, status code, and/or date range, limited to what the current user is allowed to see.",
                    objectParams(
                            new LinkedHashMap<>(Map.of(
                                    "created_by", Map.of("type", "string"),
                                    "applicant_name", stringProp("Full or partial applicant name"),
                                    "applicant_email", stringProp("Full or partial applicant email address"),
                                    "status_code", statusCodeProp(),
                                    "start_date", stringProp("YYYY-MM-DD"),
                                    "end_date", stringProp("YYYY-MM-DD"),
                                    "offset", intProp("Number of matching records to skip, for pagination. Use this when the user asks for more results using the same filters. Defaults to 0.")
                            )),
                            List.of()
                    ))
    );

    private static Map<String, Object> statusCodeProp() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "string");
        m.put("enum", STATUS_CODES);
        m.put("description", "Must be one of the exact listed status codes — never a guessed or paraphrased value.");
        return m;
    }
}
