package com.hospital.expense.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hospital.expense.model.RecurringRule;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecurrenceTest {
    private static RecurringRule rule(String frequency, String start, String end, int occurrences) {
        RecurringRule r = new RecurringRule();
        r.frequency = frequency;
        r.startDate = LocalDate.parse(start);
        r.endDate = end == null ? null : LocalDate.parse(end);
        r.occurrences = occurrences;
        return r;
    }

    @Test
    void monthEndDatesDoNotDrift() {
        LocalDate start = LocalDate.parse("2026-01-31");
        assertEquals(LocalDate.parse("2026-02-28"), Recurrence.nth(start, "MONTHLY", 1));
        assertEquals(LocalDate.parse("2026-03-31"), Recurrence.nth(start, "MONTHLY", 2));
        assertEquals(LocalDate.parse("2026-04-30"), Recurrence.nth(start, "QUARTERLY", 1));
    }

    @Test
    void weeklyAndYearlySeries() {
        assertEquals(LocalDate.parse("2026-01-29"), Recurrence.nth(LocalDate.parse("2026-01-15"), "WEEKLY", 2));
        assertEquals(LocalDate.parse("2025-02-28"), Recurrence.nth(LocalDate.parse("2024-02-29"), "YEARLY", 1));
    }

    @Test
    void listsEveryMissedDateUpToToday() {
        List<LocalDate> due = Recurrence.due(rule("MONTHLY", "2026-01-15", null, 0), LocalDate.parse("2026-03-20"));
        assertEquals(List.of(LocalDate.parse("2026-01-15"), LocalDate.parse("2026-02-15"), LocalDate.parse("2026-03-15")), due);
    }

    @Test
    void skipsWhatWasAlreadyGeneratedAndStopsAtTheEndDate() {
        assertEquals(List.of(LocalDate.parse("2026-03-15")), Recurrence.due(rule("MONTHLY", "2026-01-15", null, 2), LocalDate.parse("2026-03-20")));
        assertEquals(2, Recurrence.due(rule("MONTHLY", "2026-01-15", "2026-02-28", 0), LocalDate.parse("2026-06-01")).size());
    }

    @Test
    void nothingIsDueBeforeTheStartDate() {
        assertEquals(0, Recurrence.due(rule("MONTHLY", "2026-05-01", null, 0), LocalDate.parse("2026-04-30")).size());
    }

    @Test
    void catchUpIsCappedAtTwelvePerRun() {
        assertEquals(12, Recurrence.due(rule("WEEKLY", "2020-01-01", null, 0), LocalDate.parse("2026-01-01")).size());
    }
}
