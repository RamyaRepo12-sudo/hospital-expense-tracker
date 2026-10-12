package com.hospital.expense.web;

import com.hospital.expense.model.RecurringRule;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class Recurrence {
    public static final Set<String> FREQUENCIES = Set.of("WEEKLY", "MONTHLY", "QUARTERLY", "YEARLY");
    private static final int MAX_PER_RUN = 12;

    private Recurrence() {}

    /** The n-th date of a series (n = 0 is the start date). Computed from the start so 31 Jan, 28 Feb, 31 Mar never drifts. */
    public static LocalDate nth(LocalDate start, String frequency, int n) {
        return switch (frequency) {
            case "WEEKLY" -> start.plusWeeks(n);
            case "MONTHLY" -> start.plusMonths(n);
            case "QUARTERLY" -> start.plusMonths(3L * n);
            case "YEARLY" -> start.plusYears(n);
            default -> throw new IllegalArgumentException("Unknown frequency " + frequency);
        };
    }

    /** Dates up to and including today that have not been generated yet (at most 12 per run, to catch up after downtime). */
    public static List<LocalDate> due(RecurringRule r, LocalDate today) {
        List<LocalDate> out = new ArrayList<>();
        int n = r.occurrences;
        while (out.size() < MAX_PER_RUN) {
            LocalDate d = nth(r.startDate, r.frequency, n);
            if (d.isAfter(today) || (r.endDate != null && d.isAfter(r.endDate))) break;
            out.add(d);
            n++;
        }
        return out;
    }
}
