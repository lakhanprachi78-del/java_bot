package com.ugrocapital.losbot.tools;

import java.util.List;
import java.util.Map;

public final class ToolSchemas {
    // Keep these aligned with StatusGroups' aliases so a value the LLM picks
    // here always resolves to every raw statuscode spelling in that branch.
    public static final List<String> STATUS_CODES = List.of(
            "pre login", "pre login review", "pre login discrepant", "sales discrepant",
            "credit assessment", "pre disbursement", "sent to lms", "sent to lms failed",
            "approved", "rejected");

    private static final Map<String, Object> STRING = Map.of("type", "string");
    private static final Map<String, Object> INTEGER = Map.of("type", "integer");

    public static final List<Map<String, Object>> SCHEMAS = List.of(
            function("get_application_by_id", "Look up one application by ID", Map.of("application_id", STRING),
                    List.of("application_id")),
            function("search_by_applicant_name", "Search applications by applicant name", Map.of("name", STRING),
                    List.of("name")),
            function("search_by_applicant_email", "Search applications by applicant email", Map.of("email", STRING),
                    List.of("email")),
            function("search_by_created_by", "Search applications by creator", Map.of("name", STRING, "offset", INTEGER),
                    List.of("name")),
            function("search_by_date", "Search applications created on a date", Map.of("date", STRING, "offset", INTEGER),
                    List.of("date")),
            function("search_by_date_range", "Search applications created between dates",
                    Map.of("start_date", STRING, "end_date", STRING, "offset", INTEGER), List.of("start_date", "end_date")),
            function("search_by_status", "Search applications by status", Map.of("status", STRING, "offset", INTEGER),
                    List.of("status")),
            function("not_in_scope", "Hand off a non-LOS question", Map.of("topic", STRING), List.of("topic")));

    private ToolSchemas() {
    }

    private static Map<String, Object> function(String name, String description, Map<String, Object> properties,
            List<String> required) {
        return Map.of("type", "function", "function", Map.of(
                "name", name,
                "description", description,
                "parameters", Map.of("type", "object", "properties", properties, "required", required)));
    }
}