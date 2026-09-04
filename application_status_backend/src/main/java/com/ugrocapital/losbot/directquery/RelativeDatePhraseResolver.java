package com.ugrocapital.losbot.directquery;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;

public final class RelativeDatePhraseResolver {
    private static final DateTimeFormatter[] DATE_FORMATS = {
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy") };

    private RelativeDatePhraseResolver() {
    }

    public static DateRange resolve(String phrase, LocalDate today) {
        String value = phrase == null ? "" : phrase.trim().toLowerCase();
        LocalDate start;
        LocalDate end;
        switch (value) {
        case "today" -> start = end = today;
        case "yesterday" -> start = end = today.minusDays(1);
        case "tomorrow" -> start = end = today.plusDays(1);
        case "this week" -> {
            start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            end = start.plusDays(6);
        }
        case "last week" -> {
            end = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusDays(1);
            start = end.minusDays(6);
        }
        case "this month" -> {
            start = today.with(TemporalAdjusters.firstDayOfMonth());
            end = today.with(TemporalAdjusters.lastDayOfMonth());
        }
        case "last month" -> {
            LocalDate month = today.minusMonths(1);
            start = month.with(TemporalAdjusters.firstDayOfMonth());
            end = month.with(TemporalAdjusters.lastDayOfMonth());
        }
        case "this year" -> {
            start = LocalDate.of(today.getYear(), Month.JANUARY, 1);
            end = LocalDate.of(today.getYear(), Month.DECEMBER, 31);
        }
        case "last year" -> {
            start = LocalDate.of(today.getYear() - 1, Month.JANUARY, 1);
            end = LocalDate.of(today.getYear() - 1, Month.DECEMBER, 31);
        }
        default -> {
            for (DateTimeFormatter formatter : DATE_FORMATS) {
                try {
                    LocalDate date = LocalDate.parse(value, formatter);
                    return new DateRange(date, date);
                } catch (DateTimeParseException ignored) {
                    // Try the next supported date format.
                }
            }
            throw new DirectQueryException("Please enter a valid date or relative date phrase.");
        }
        }
        return new DateRange(start, end);
    }

    public record DateRange(LocalDate start, LocalDate end) {
    }
}