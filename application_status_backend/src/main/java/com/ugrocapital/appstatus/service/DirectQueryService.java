package com.ugrocapital.appstatus.service;

import com.ugrocapital.appstatus.auth.AuthContext;
import com.ugrocapital.appstatus.dto.ApplicationDto;
import com.ugrocapital.appstatus.dto.SearchResult;
import com.ugrocapital.appstatus.repository.LoanApplicationRepository;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

/**
 * Direct (non-LLM) query path.
 *
 * The frontend's query-type menu already tells us EXACTLY which
 * repository function to call and with what single parameter, before the
 * request ever reaches the backend — the user picked a status from the
 * menu, or typed into a field that was already validated as an
 * application ID, a date phrase, etc. Turning that into a fake sentence
 * like "Show applications with status: X" and asking an LLM to re-parse
 * it back into a tool call would be a pure round-trip: tokens spent to
 * undo work the UI already did.
 *
 * This class is that shortcut — used for the "pure" cases only: field +
 * value (+ offset), nothing else bundled in. The moment the user adds
 * actual free text, that genuinely needs language understanding and goes
 * through ChatEngine.runChatTurn instead. See ChatController's
 * /chat.direct handler for where that split happens.
 *
 * Nothing here calls LlmClient, ToolDispatcher, or touches SYSTEM_PROMPT.
 * Every method still goes through LoanApplicationRepository, so the same
 * ownership/auth scoping and sensitive-data redaction apply exactly as
 * they do on the LLM-routed path — this is a shortcut around the LLM,
 * never around authorization. Direct port of direct_queries.py.
 */
@Service
public class DirectQueryService {

    private final LoanApplicationRepository repository;
    private final FormattingService formatting;

    public DirectQueryService(LoanApplicationRepository repository, FormattingService formatting) {
        this.repository = repository;
        this.formatting = formatting;
    }

    public record DirectQueryResult(String reply, Boolean hasMore) {
    }

    // --- date_time field: resolve without an LLM -------------------------
    // Mirrors the small set of relative phrases the frontend's date_time
    // field already accepts (RELATIVE_DATE_PATTERN in chat.models.ts)
    // plus a literal YYYY-MM-DD / DD-MM-YYYY / DD/MM/YYYY date. If a
    // phrase doesn't match anything here, DirectQueryException is thrown
    // and the caller falls back to routing the raw text through the LLM
    // instead — this is a fast path for the common cases, not a full
    // replacement for language understanding.

    private static final DateTimeFormatter[] DATE_FORMATS = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy")
    };

    private LocalDate parseLiteralDate(String text) {
        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try {
                return LocalDate.parse(text.strip(), fmt);
            } catch (DateTimeParseException ignored) {
                // try next format
            }
        }
        return null;
    }

    public record DateRange(LocalDate start, LocalDate end) {
    }

    /** Returns an inclusive (start, end) range for a relative or literal
     * date phrase. Throws DirectQueryException if unrecognized. */
    public DateRange resolveDatePhrase(String phrase) {
        String p = phrase.strip().toLowerCase();
        LocalDate today = LocalDate.now();

        LocalDate literal = parseLiteralDate(phrase);
        if (literal != null) {
            return new DateRange(literal, literal);
        }

        switch (p) {
            case "today":
                return new DateRange(today, today);
            case "yesterday": {
                LocalDate d = today.minusDays(1);
                return new DateRange(d, d);
            }
            case "tomorrow": {
                LocalDate d = today.plusDays(1);
                return new DateRange(d, d);
            }
            case "this week": {
                LocalDate start = today.minusDays(today.getDayOfWeek().getValue() - DayOfWeek.MONDAY.getValue());
                return new DateRange(start, today);
            }
            case "last week": {
                LocalDate thisWeekStart = today.minusDays(today.getDayOfWeek().getValue() - DayOfWeek.MONDAY.getValue());
                LocalDate start = thisWeekStart.minusDays(7);
                LocalDate end = thisWeekStart.minusDays(1);
                return new DateRange(start, end);
            }
            case "this month":
                return new DateRange(today.withDayOfMonth(1), today);
            case "last month": {
                LocalDate firstOfThisMonth = today.withDayOfMonth(1);
                LocalDate lastMonthEnd = firstOfThisMonth.minusDays(1);
                return new DateRange(lastMonthEnd.withDayOfMonth(1), lastMonthEnd);
            }
            case "this year":
                return new DateRange(today.withDayOfYear(1), today);
            case "last year": {
                int lastYear = today.getYear() - 1;
                return new DateRange(LocalDate.of(lastYear, 1, 1), LocalDate.of(lastYear, 12, 31));
            }
            default:
                throw new DirectQueryException(
                        "I couldn't understand \"" + phrase + "\" as a date. Try a specific date " +
                                "(e.g. 2026-08-16) or a phrase like \"today\" or \"last week\".");
        }
    }

    private static final Pattern APPLICATION_ID_RE = Pattern.compile("^[A-Za-z0-9-]{5,30}$");

    /**
     * field: one of "application_id", "status", "applicant_name",
     *        "applicant_email", "date_time" — matches the frontend's
     *        QUERY_TYPES keys (plus "status" for the status-picker flow).
     * value: the already-validated single value the UI collected.
     * offset: pagination offset (0 for a fresh query).
     */
    public DirectQueryResult runDirectQuery(String field, String value, int offset, AuthContext auth) {
        String v = value == null ? "" : value.strip();
        if (v.isEmpty()) {
            throw new DirectQueryException("Please provide a value to search for.");
        }

        switch (field) {
            case "application_id": {
                if (!APPLICATION_ID_RE.matcher(v).matches()) {
                    throw new DirectQueryException("That doesn't look like a valid application ID.");
                }
                ApplicationDto app = repository.getApplicationById(v, auth);
                return new DirectQueryResult(formatting.formatSingleLookupReply(app), null);
            }

            case "status": {
                String statusCode = v.toLowerCase();
                if (!ToolSchemas.STATUS_CODES.contains(statusCode)) {
                    throw new DirectQueryException(
                            "'" + value + "' isn't a recognized status. Must be one of: "
                                    + String.join(", ", ToolSchemas.STATUS_CODES) + ".");
                }
                SearchResult result = repository.searchByStatus(statusCode, auth, offset);
                String reply = formatting.formatSearchReply(
                        result, "with status " + statusCode, offset, "a date, date range, or application ID");
                return new DirectQueryResult(reply, result.hasMore());
            }

            case "applicant_name": {
                SearchResult result = repository.searchByApplicantName(v, auth);
                String reply = formatting.formatSearchReply(
                        result, "for applicant name matching \"" + v + "\"", 0, "a status, date, or application ID");
                return new DirectQueryResult(reply, result.hasMore());
            }

            case "applicant_email": {
                SearchResult result = repository.searchByApplicantEmail(v, auth);
                String reply = formatting.formatSearchReply(
                        result, "for applicant email matching \"" + v + "\"", 0, "a status, date, or application ID");
                return new DirectQueryResult(reply, result.hasMore());
            }

            case "date_time": {
                DateRange range = resolveDatePhrase(v);
                SearchResult result = repository.searchByDateRange(range.start(), range.end(), auth, offset);
                String label = range.start().equals(range.end())
                        ? range.start().toString()
                        : range.start() + " to " + range.end();
                String reply = formatting.formatSearchReply(
                        result, "created on " + label, offset, "a status or application ID");
                return new DirectQueryResult(reply, result.hasMore());
            }

            default:
                throw new DirectQueryException("Unknown query field: " + field);
        }
    }
}
