package com.ugrocapital.losbot.repository;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.ugrocapital.losbot.entity.ApplicationApplicant;
import com.ugrocapital.losbot.entity.ApplicationApplicantEmail;
import com.ugrocapital.losbot.entity.LoanApplication;

@Component
public class ApplicationSerializer {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm:ss", Locale.ENGLISH);
    private static final Pattern PAN = Pattern.compile("\\b[A-Z]{5}[0-9]{4}[A-Z]\\b");
    private static final Pattern SECRET = Pattern.compile(
            "(?i)\\b(password|passwd|otp|pin)\\b\\s*[:=]?\\s*\\S+");
    private static final Pattern DIGITS = Pattern.compile("\\b\\d{9,}\\b");
    private static final int MAX_REMARK_LENGTH = 500;

    public ApplicationDto serialize(LoanApplication application) {
        List<ApplicationApplicant> applicants = application.getApplicants() == null
                ? List.of() : application.getApplicants();
        return new ApplicationDto(
                application.getApplicationId(),
                application.getStatusCode(),
                application.getCreatedBy(),
                format(application.getCreatedDate()),
                application.getLastUpdatedBy(),
                format(application.getLastUpdatedDate()),
                redact(application.getRemarks()),
                namesOf(applicants, "APPLICANT"),
                namesOf(applicants, "COAPPLICANT"),
                namesOf(applicants, "GUARANTOR"),
                applicants.stream()
                        .filter(applicant -> "APPLICANT".equalsIgnoreCase(applicant.getApplicantType()))
                        .flatMap(applicant -> applicant.getEmails() == null ? java.util.stream.Stream.empty()
                                : applicant.getEmails().stream())
                        .map(ApplicationApplicantEmail::getEmailAddress)
                        .filter(Objects::nonNull)
                        .toList());
    }

    public String redact(String value) {
        if (value == null) {
            return null;
        }
        String redacted = PAN.matcher(value).replaceAll("[REDACTED]");
        redacted = SECRET.matcher(redacted).replaceAll("$1=[REDACTED]");
        redacted = DIGITS.matcher(redacted).replaceAll("[REDACTED]");
        if (redacted.length() > MAX_REMARK_LENGTH) {
            return redacted.substring(0, MAX_REMARK_LENGTH) + "... [truncated]";
        }
        return redacted;
    }

    private List<String> namesOf(List<ApplicationApplicant> applicants, String type) {
        return applicants.stream()
                .filter(applicant -> type.equalsIgnoreCase(applicant.getApplicantType()))
                .map(ApplicationApplicant::getName)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private String format(java.time.LocalDateTime value) {
        return value == null ? null : DATE_FORMAT.format(value);
    }
}