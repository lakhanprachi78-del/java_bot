package com.ugrocapital.losbot.directquery;

import java.time.LocalDate;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.ugrocapital.losbot.auth.AuthContext;
import com.ugrocapital.losbot.format.ReplyFormatter;
import com.ugrocapital.losbot.repository.ApplicationQueryRepository;
import com.ugrocapital.losbot.repository.SearchResult;

@Service
public class DirectQueryService {
    private static final Pattern APPLICATION_ID = Pattern.compile("^[A-Za-z0-9-]{5,30}$");
    private final ApplicationQueryRepository repository;

    public DirectQueryService(ApplicationQueryRepository repository) {
        this.repository = repository;
    }

    public String runDirectQuery(String field, String value, int offset, AuthContext auth) {
        return runDirectQueryResult(field, value, offset, auth).reply();
    }

    public DirectQueryResult runDirectQueryResult(String field, String value, int offset, AuthContext auth) {
        if (field == null || value == null || value.isBlank()) {
            throw new DirectQueryException("Please provide a search value.");
        }
        String normalizedField = field.trim().toLowerCase();
        String normalizedValue = value.trim();
        return switch (normalizedField) {
        case "application_id" -> lookupApplicationResult(normalizedValue, auth);
        case "status" -> searchResult(repository.searchByStatus(normalizedValue, auth, offset, 5));
        case "applicant_name" -> searchResult(repository.searchByApplicantName(normalizedValue, auth, 5));
        case "applicant_email" -> searchResult(repository.searchByApplicantEmail(normalizedValue, auth, 5));
        case "date_time" -> searchByDate(normalizedValue, offset, auth);
        default -> throw new DirectQueryException("Unsupported direct-query field: " + field);
        };
    }

    private DirectQueryResult lookupApplicationResult(String value, AuthContext auth) {
        if (!APPLICATION_ID.matcher(value).matches()) {
            throw new DirectQueryException("Please enter a valid Application ID / LAF ID.");
        }
        return new DirectQueryResult(
                ReplyFormatter.formatSingleLookupReply(repository.getApplicationById(value, auth).orElse(null)), false);
    }

    private DirectQueryResult searchByDate(String value, int offset, AuthContext auth) {
        RelativeDatePhraseResolver.DateRange range = RelativeDatePhraseResolver.resolve(value, LocalDate.now());
        SearchResult result = range.start().equals(range.end())
                ? repository.searchByDate(range.start(), auth, offset, 5)
                : repository.searchByDateRange(range.start(), range.end(), auth, offset, 5);
        return searchResult(result);
    }

    private DirectQueryResult searchResult(SearchResult result) {
        return new DirectQueryResult(ReplyFormatter.formatSearchReply(result), result.hasMore());
    }

    public record DirectQueryResult(String reply, boolean hasMore) {
    }
}