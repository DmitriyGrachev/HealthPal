package com.fit.fitnessapp.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;

public record DateRange(LocalDate startDate, LocalDate endDate) {
    public DateRange {
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("Start date and end date must not be null");
        }
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("Start date cannot be after end date");
        }
    }

    public static DateRange of(LocalDate startDate, LocalDate endDate) {
        return new DateRange(startDate, endDate);
    }

    public static DateRange isoWeek(LocalDate date) {
        LocalDate start = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate end = start.plusDays(6);
        return new DateRange(start, end);
    }

    public static DateRange calendarMonth(YearMonth month) {
        LocalDate start = month.atDay(1);
        LocalDate end = month.atEndOfMonth();
        return new DateRange(start, end);
    }

    public boolean contains(LocalDate date) {
        if (date == null) return false;
        return !date.isBefore(startDate) && !date.isAfter(endDate);
    }

    public long getDays() {
        return startDate.until(endDate).getDays() + 1;
    }
}
