package com.ugrocapital.losbot.repository;

import java.util.List;

public record ApplicationDto(
        String applicationId,
        String statusCode,
        String createdBy,
        String createdDate,
        String lastUpdatedBy,
        String lastUpdatedDate,
        String remarks,
        List<String> applicantNames,
        List<String> coApplicantNames,
        List<String> guarantorNames,
        List<String> applicantEmails) {
}