package com.ugrocapital.losbot.repository;

import java.time.LocalDate;

public record CombinedSearchParams(
        String applicationId,
        String statusCode,
        String applicantName,
        String applicantEmail,
        String createdBy,
        LocalDate date,
        LocalDate startDate,
        LocalDate endDate,
        int offset,
        int limit) {
}