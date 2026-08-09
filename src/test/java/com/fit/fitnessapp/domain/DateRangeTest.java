package com.fit.fitnessapp.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DateRangeTest {

    @Test
    @DisplayName("ISO week should start on Monday and end on Sunday")
    void isoWeek_StartsMondayEndsSunday() {
        LocalDate wednesday = LocalDate.of(2026, 8, 12);
        DateRange range = DateRange.isoWeek(wednesday);

        assertThat(range.startDate()).isEqualTo(LocalDate.of(2026, 8, 10));
        assertThat(range.endDate()).isEqualTo(LocalDate.of(2026, 8, 16));
        assertThat(range.getDays()).isEqualTo(7);
    }

    @Test
    @DisplayName("Calendar month should handle leap years correctly")
    void calendarMonth_HandlesLeapYear() {
        DateRange leapFeb = DateRange.calendarMonth(YearMonth.of(2024, 2));
        assertThat(leapFeb.startDate()).isEqualTo(LocalDate.of(2024, 2, 1));
        assertThat(leapFeb.endDate()).isEqualTo(LocalDate.of(2024, 2, 29));
        assertThat(leapFeb.getDays()).isEqualTo(29);

        DateRange normalFeb = DateRange.calendarMonth(YearMonth.of(2025, 2));
        assertThat(normalFeb.endDate()).isEqualTo(LocalDate.of(2025, 2, 28));
        assertThat(normalFeb.getDays()).isEqualTo(28);
    }

    @Test
    @DisplayName("Should throw exception when start date is after end date")
    void constructor_ValidatesRange() {
        assertThatThrownBy(() -> DateRange.of(LocalDate.of(2026, 8, 15), LocalDate.of(2026, 8, 10)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
