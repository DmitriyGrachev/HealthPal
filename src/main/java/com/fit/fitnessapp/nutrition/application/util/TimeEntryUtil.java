package com.fit.fitnessapp.nutrition.application.util;
import java.time.YearMonth;
import java.time.Clock;


public class TimeEntryUtil {

    private final Clock clock;

    public TimeEntryUtil(Clock clock) {
        this.clock = clock;
    }

    public TimeEntryUtil() {
        this(Clock.systemUTC());
    }

    public int getCurrentDaysInCurrentMonth(){
        YearMonth now = YearMonth.now(clock);
        int days = now.lengthOfMonth();
        return days;
    }


}
