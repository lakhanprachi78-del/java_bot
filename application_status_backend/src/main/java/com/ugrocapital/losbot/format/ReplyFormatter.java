package com.ugrocapital.losbot.format;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.ugrocapital.losbot.repository.ApplicationDto;
import com.ugrocapital.losbot.repository.SearchResult;

public final class ReplyFormatter {

    private ReplyFormatter() {
    }

    public static String formatApplicationBlock(ApplicationDto application) {
        List<String> lines = new ArrayList<>();
        add(lines, "Application ID", application.applicationId());
        add(lines, "Status", upper(application.statusCode()));
        add(lines, "Created by", application.createdBy());
        add(lines, "Created", application.createdDate());
        add(lines, "Last updated by", application.lastUpdatedBy());
        add(lines, "Last updated", application.lastUpdatedDate());
        addNames(lines, "Applicant", application.applicantNames());
        addNames(lines, "Co-applicant", application.coApplicantNames());
        addNames(lines, "Guarantor", application.guarantorNames());
        addNames(lines, "Applicant email", application.applicantEmails());
        add(lines, "Remarks", application.remarks());
        return String.join("\n", lines);
    }

    public static String formatApplicationBlocks(List<ApplicationDto> applications) {
        if (applications == null || applications.isEmpty()) {
            return "No applications found.";
        }
        return applications.stream()
                .map(ReplyFormatter::formatApplicationBlock)
                .filter(block -> !block.isBlank())
                .reduce((left, right) -> left + "\n\n" + right)
                .orElse("No applications found.");
    }

    public static String formatSearchReply(SearchResult searchResult) {
        return formatSearchReply(searchResult, null);
    }

    public static String formatSearchReply(SearchResult searchResult, String narrowHint) {
        if (searchResult == null || searchResult.results() == null || searchResult.results().isEmpty()) {
            return "No applications found.";
        }

        StringBuilder reply = new StringBuilder();
        reply.append("There are ")
                .append(searchResult.totalMatches())
                .append(" applications matching your search — showing ")
                .append(searchResult.returned())
                .append(" most recent.")
                .append("\n\n")
                .append(formatApplicationBlocks(searchResult.results()));

        if (searchResult.hasMore()) {
            reply.append("\n\nThere are more matching applications. ")
                    .append(narrowHint == null || narrowHint.isBlank()
                            ? "Use the offset to see the next results."
                            : narrowHint.trim());
        }
        return reply.toString();
    }

    public static String formatSingleLookupReply(ApplicationDto application) {
        return application == null ? "No application found." : formatApplicationBlock(application);
    }

    private static void add(List<String> lines, String label, String value) {
        if (value != null && !value.isBlank()) {
            lines.add(label + ": " + value.trim());
        }
    }

    private static void addNames(List<String> lines, String label, List<String> values) {
        if (values != null) {
            String joined = values.stream()
                    .filter(value -> value != null && !value.isBlank())
                    .map(String::trim)
                    .reduce((left, right) -> left + ", " + right)
                    .orElse("");
            add(lines, label, joined);
        }
    }

    private static String upper(String value) {
        return value == null ? null : value.toUpperCase(Locale.ROOT);
    }
}