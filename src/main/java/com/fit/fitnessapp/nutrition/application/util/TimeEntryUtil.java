package com.fit.fitnessapp.nutrition.application.util;
import java.time.YearMonth;


public class TimeEntryUtil {


    public int getCurrentDaysInCurrentMonth(){
        YearMonth now = YearMonth.now();
        int days = now.lengthOfMonth();
        return days;
    }


}
