package com.ugrocapital.appstatus.service;

import com.ugrocapital.appstatus.auth.AuthContext;
import com.ugrocapital.appstatus.dto.SearchResult;
import com.ugrocapital.appstatus.repository.LoanApplicationRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Safely routes a validated tool call to the repository layer. `auth`
 * comes from the server (resolved from session_id) — never from
 * `arguments`, which is LLM/user-influenced. Direct port of tools.py's
 * dispatch().
 */
@Component
public class ToolDispatcher {

    private final LoanApplicationRepository repository;

    public ToolDispatcher(LoanApplicationRepository repository) {
        this.repository = repository;
    }

    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    private LocalDate parseDate(String s) {
        try {
            return LocalDate.parse(s.strip(), ISO_DATE);
        } catch (DateTimeParseException e) {
            throw new ToolException("Invalid date format: '" + s + "'. Expected YYYY-MM-DD.");
        }
    }

    private int offsetOf(Map<String, Object> args) {
        Object o = args.get("offset");
        if (o == null) return 0;
        return ((Number) o).intValue();
    }

    private String requireString(Map<String, Object> args, String key) {
        Object v = args.get(key);
        if (v == null) {
            throw new ToolException("Missing required argument: " + key);
        }
        return v.toString();
    }

    /** Returns a plain Map result (mirrors the Python dict tool results)
     * so it can be serialized straight back to the LLM as JSON. */
    public Map<String, Object> dispatch(String toolName, Map<String, Object> arguments, AuthContext auth) {
        switch (toolName) {
            case "get_application_by_id": {
                var app = repository.getApplicationById(requireString(arguments, "application_id"), auth);
                Map<String, Object> result = new LinkedHashMap<>();
                if (app != null) {
                    result.put("found", true);
                    result.put("application", app);
                } else {
                    result.put("found", false);
                    result.put("message", "No application found with that ID.");
                }
                return result;
            }

            case "search_by_created_by": {
                int offset = offsetOf(arguments);
                SearchResult r = repository.searchByCreatedBy(requireString(arguments, "name"), auth, offset);
                return toMap(r);
            }

            case "search_by_applicant_name": {
                SearchResult r = repository.searchByApplicantName(requireString(arguments, "name"), auth);
                return toMap(r);
            }

            case "search_by_applicant_email": {
                SearchResult r = repository.searchByApplicantEmail(requireString(arguments, "email"), auth);
                return toMap(r);
            }

            case "search_by_date": {
                LocalDate d = parseDate(requireString(arguments, "date"));
                int offset = offsetOf(arguments);
                SearchResult r = repository.searchByDate(d, auth, offset);
                return toMap(r);
            }

            case "search_by_date_range": {
                LocalDate start = parseDate(requireString(arguments, "start_date"));
                LocalDate end = parseDate(requireString(arguments, "end_date"));
                if (start.isAfter(end)) {
                    throw new ToolException("start_date must be before end_date.");
                }
                int offset = offsetOf(arguments);
                SearchResult r = repository.searchByDateRange(start, end, auth, offset);
                return toMap(r);
            }

            case "search_by_status": {
                String statusCode = requireString(arguments, "status_code");
                if (!ToolSchemas.STATUS_CODES.contains(statusCode)) {
                    throw new ToolException("Invalid status_code: '" + statusCode + "'. Must be one of: "
                            + String.join(", ", ToolSchemas.STATUS_CODES) + ".");
                }
                int offset = offsetOf(arguments);
                SearchResult r = repository.searchByStatus(statusCode, auth, offset);
                return toMap(r);
            }

            case "combined_search": {
                String startDateStr = (String) arguments.get("start_date");
                String endDateStr = (String) arguments.get("end_date");
                LocalDate start = (startDateStr != null && !startDateStr.isBlank()) ? parseDate(startDateStr) : null;
                LocalDate end = (endDateStr != null && !endDateStr.isBlank()) ? parseDate(endDateStr) : null;
                if (start != null && end != null && start.isAfter(end)) {
                    throw new ToolException("start_date must be before end_date.");
                }

                String statusCode = (String) arguments.get("status_code");
                if (statusCode != null && !statusCode.isBlank() && !ToolSchemas.STATUS_CODES.contains(statusCode)) {
                    throw new ToolException("Invalid status_code: '" + statusCode + "'. Must be one of: "
                            + String.join(", ", ToolSchemas.STATUS_CODES) + ".");
                }

                int offset = offsetOf(arguments);

                SearchResult r = repository.combinedSearch(
                        auth,
                        (String) arguments.get("created_by"),
                        statusCode,
                        start,
                        end,
                        (String) arguments.get("applicant_name"),
                        (String) arguments.get("applicant_email"),
                        LoanApplicationRepository.DEFAULT_RESULT_LIMIT,
                        offset
                );
                return toMap(r);
            }

            default:
                throw new ToolException("Unknown tool: " + toolName);
        }
    }

    private Map<String, Object> toMap(SearchResult r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("results", r.results());
        m.put("total_matches", r.totalMatches());
        m.put("returned", r.returned());
        m.put("has_more", r.hasMore());
        return m;
    }
}
