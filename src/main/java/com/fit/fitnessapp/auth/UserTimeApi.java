package com.fit.fitnessapp.auth;

import java.time.LocalDate;

public interface UserTimeApi {
    LocalDate currentDate(Long userId);

    String getTimeZone(Long userId);

    void setTimeZone(Long userId, String ianaTimeZone);
}
