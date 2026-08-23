package com.fit.fitnessapp.auth;

import com.fit.fitnessapp.ai.RateLimitInterceptor;
import com.fit.fitnessapp.auth.adapter.in.web.UserNoteController;
import com.fit.fitnessapp.auth.application.port.in.UserNoteUseCase;
import com.fit.fitnessapp.auth.infrastructure.utils.TokenFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserNoteController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserNoteControllerValidationTest {

    @MockitoBean private UserNoteUseCase userNoteUseCase;
    @MockitoBean private CurrentUserApi currentUserApi;
    @MockitoBean private RateLimitInterceptor rateLimitInterceptor;
    @MockitoBean private TokenFilter tokenFilter;
    @MockitoBean private UserDetailsService userDetailsService;

    @Autowired private MockMvc mockMvc;

    @Test
    void createNoteRejectsInvalidPayloadBeforeCallingUseCase() throws Exception {
        when(currentUserApi.getCurrentUserId()).thenReturn(7L);

        mockMvc.perform(post("/api/v1/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":999,"relatedDate":null,"content":" ","type":null}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.relatedDate").exists())
                .andExpect(jsonPath("$.fieldErrors.content").exists())
                .andExpect(jsonPath("$.fieldErrors.type").exists());

        verify(userNoteUseCase, never()).createNote(any());
    }
}
