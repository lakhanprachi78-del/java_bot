package com.ugrocapital.losbot.directquery;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class RelativeDatePhraseResolverTest {
    private final LocalDate today = LocalDate.of(2026, 9, 3);

    @Test
    void resolvesRelativeDates() {
        assertEquals(today, RelativeDatePhraseResolver.resolve("today", today).start());
        assertEquals(LocalDate.of(2026, 8, 31), RelativeDatePhraseResolver.resolve("this week", today).start());
        assertEquals(LocalDate.of(2026, 8, 31), RelativeDatePhraseResolver.resolve("last month", today).end());
    }

    @Test
    void resolvesSupportedDateFormats() {
        assertEquals(today, RelativeDatePhraseResolver.resolve("2026-09-03", today).start());
        assertEquals(today, RelativeDatePhraseResolver.resolve("03/09/2026", today).start());
    }
}