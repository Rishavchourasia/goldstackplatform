package com.goldstack.common;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

public enum Frequency {
    DAILY, WEEKLY, MONTHLY;

    public LocalDate nth(LocalDate start, long n) {
        return switch (this) {
            case DAILY -> start.plusDays(n);
            case WEEKLY -> start.plusWeeks(n);
            case MONTHLY -> start.plusMonths(n);
        };
    }

    public LocalDate firstOnOrAfter(LocalDate start, LocalDate date) {
        if (!date.isAfter(start)) {
            return start;
        }

        long n = switch (this) {
            case DAILY -> ChronoUnit.DAYS.between(start, date);
            case WEEKLY -> ChronoUnit.WEEKS.between(start, date);
            case MONTHLY -> ChronoUnit.MONTHS.between(start, date);
        };
        n = Math.max(0, n - 1);
        LocalDate candidate = nth(start, n);
        while (candidate.isBefore(date)) {
            n++;
            candidate = nth(start, n);
        }
        return candidate;
    }

    public LocalDate firstAfter(LocalDate start, LocalDate date) {
        return firstOnOrAfter(start, date.plusDays(1));
    }

    public List<LocalDate> scheduleBetween(LocalDate start, LocalDate end) {
        List<LocalDate> dates = new ArrayList<>();
        for (long n = 0; ; n++) {
            LocalDate d = nth(start, n);
            if (d.isAfter(end)) {
                return dates;
            }
            dates.add(d);
        }
    }
}
