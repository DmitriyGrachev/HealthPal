package com.fit.fitnessapp.auth;

import com.fit.fitnessapp.auth.adapter.in.web.UserTimeController;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserTimeControllerTest {

    @Test
    void readsTimezoneAndCurrentDateThroughAuthenticatedUser() {
        CurrentUserApi currentUserApi = mock(CurrentUserApi.class);
        UserTimeApi userTimeApi = mock(UserTimeApi.class);
        when(currentUserApi.getCurrentUserId()).thenReturn(7L);
        when(userTimeApi.getTimeZone(7L)).thenReturn("Europe/Chisinau");

        UserTimeController controller = new UserTimeController(currentUserApi, userTimeApi);

        assertThat(controller.getTimeZone().getBody().ianaTimeZone()).isEqualTo("Europe/Chisinau");
        verify(userTimeApi).getTimeZone(7L);
    }

    @Test
    void updatesTimezoneForAuthenticatedUserOnly() {
        CurrentUserApi currentUserApi = mock(CurrentUserApi.class);
        UserTimeApi userTimeApi = mock(UserTimeApi.class);
        when(currentUserApi.getCurrentUserId()).thenReturn(7L);
        when(userTimeApi.getTimeZone(7L)).thenReturn("America/New_York");

        UserTimeController controller = new UserTimeController(currentUserApi, userTimeApi);

        var response = controller.setTimeZone(new UserTimeController.TimeZoneRequest("America/New_York"));

        assertThat(response.getBody().ianaTimeZone()).isEqualTo("America/New_York");
        verify(userTimeApi).setTimeZone(7L, "America/New_York");
    }
}
