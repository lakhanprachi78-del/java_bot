package com.ugrocapital.losbot.tools;

import java.time.LocalDate;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.ugrocapital.losbot.auth.AuthContext;
import com.ugrocapital.losbot.repository.ApplicationQueryRepository;
import com.ugrocapital.losbot.repository.CombinedSearchParams;

@Service
public class ToolDispatcher {
    private final ApplicationQueryRepository repository;

    public ToolDispatcher(ApplicationQueryRepository repository) {
        this.repository = repository;
    }

    public Object dispatch(String toolName, Map<String, Object> arguments, AuthContext auth) {
        if (toolName == null || arguments == null || auth == null) {
            throw new ToolException("Tool name, arguments, and authentication are required.");
        }
        int offset = integer(arguments, "offset", 0);
        return switch (toolName) {
        case "get_application_by_id" -> repository.getApplicationById(string(arguments, "application_id"), auth);
        case "search_by_applicant_name" -> repository.searchByApplicantName(string(arguments, "name"), auth, 5);
        case "search_by_applicant_email" -> repository.searchByApplicantEmail(string(arguments, "email"), auth, 5);
        case "search_by_created_by" -> repository.searchByCreatedBy(string(arguments, "name"), auth, offset, 5);
        case "search_by_date" -> repository.searchByDate(date(arguments, "date"), auth, offset, 5);
        case "search_by_date_range" -> repository.searchByDateRange(date(arguments, "start_date"),
                date(arguments, "end_date"), auth, offset, 5);
        case "search_by_status" -> repository.searchByStatus(string(arguments, "status"), auth, offset, 5);
        case "not_in_scope" -> throw new ToolException("OUT_OF_SCOPE: " + string(arguments, "topic"));
        default -> throw new ToolException("Unknown tool: " + toolName);
        };
    }

    private String string(Map<String, Object> arguments, String name) {
        Object value = arguments.get(name);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new ToolException("Missing tool argument: " + name);
        }
        return text.trim();
    }

    private int integer(Map<String, Object> arguments, String name, int fallback) {
        Object value = arguments.get(name);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException exception) {
            throw new ToolException("Invalid integer tool argument: " + name);
        }
    }

    private LocalDate date(Map<String, Object> arguments, String name) {
        try {
            return LocalDate.parse(string(arguments, name));
        } catch (RuntimeException exception) {
            throw new ToolException("Invalid date tool argument: " + name);
        }
    }
}