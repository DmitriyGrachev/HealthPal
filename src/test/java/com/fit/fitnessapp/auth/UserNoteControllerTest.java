package com.fit.fitnessapp.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fit.fitnessapp.TestConfig;
import com.fit.fitnessapp.auth.adapter.in.web.UserNoteController;
import com.fit.fitnessapp.auth.application.port.in.UserNoteUseCase;
import com.fit.fitnessapp.auth.domain.UserNoteDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserNoteController.class)
@Import(TestConfig.class)
class UserNoteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserNoteUseCase userNoteUseCase;

    private UserNoteDto testNote;

    @BeforeEach
    void setUp() {
        testNote = new UserNoteDto(
                null,
                1L,
                LocalDate.of(2026, 4, 15),
                "Test note content",
                UserNoteDto.NoteType.ILLNESS
        );
    }

    @Test
    @WithMockUser
    void createNote_shouldReturn200WhenAuthenticated() throws Exception {
        when(userNoteUseCase.createNote(any(UserNoteDto.class)))
                .thenReturn(new UserNoteDto(1L, 1L, LocalDate.of(2026, 4, 15), "Test note content", UserNoteDto.NoteType.ILLNESS));

        mockMvc.perform(post("/api/v1/notes")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testNote)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.content").value("Test note content"))
                .andExpect(jsonPath("$.type").value("ILLNESS"));
    }

    @Test
    void createNote_shouldReturn401WhenNotAuthenticated() throws Exception {
        mockMvc.perform(post("/api/v1/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testNote)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void getNotes_shouldReturnNotesForUser() throws Exception {
        List<UserNoteDto> notes = List.of(
                new UserNoteDto(1L, 1L, LocalDate.of(2026, 4, 15), "First note", UserNoteDto.NoteType.ILLNESS)
        );
        when(userNoteUseCase.getNotesByUserId(1L)).thenReturn(notes);

        mockMvc.perform(get("/api/v1/notes")
                        .param("userId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].content").value("First note"));
    }

    @Test
    @WithMockUser
    void getNotes_shouldReturnEmptyListWhenNoNotes() throws Exception {
        when(userNoteUseCase.getNotesByUserId(1L)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/notes")
                        .param("userId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @WithMockUser
    void getNotes_withDateRange_shouldReturnFilteredNotes() throws Exception {
        List<UserNoteDto> notes = List.of(
                new UserNoteDto(1L, 1L, LocalDate.of(2026, 4, 10), "Note in range", UserNoteDto.NoteType.TRAVEL)
        );
        when(userNoteUseCase.getNotesByUserIdAndDateRange(any(), any(), any())).thenReturn(notes);

        mockMvc.perform(get("/api/v1/notes")
                        .param("userId", "1")
                        .param("from", "2026-04-01")
                        .param("to", "2026-04-20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].content").value("Note in range"));
    }

    @Test
    @WithMockUser
    void deleteNote_shouldReturn204WhenAuthenticated() throws Exception {
        mockMvc.perform(delete("/api/v1/notes/1")
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteNote_shouldReturn401WhenNotAuthenticated() throws Exception {
        mockMvc.perform(delete("/api/v1/notes/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void createNote_withInvalidType_shouldReturn5xx() throws Exception {
        String invalidJson = "{\"userId\":1,\"relatedDate\":\"2026-04-15\",\"content\":\"test\",\"type\":\"INVALID_TYPE\"}";

        when(userNoteUseCase.createNote(any(UserNoteDto.class)))
                .thenThrow(new IllegalArgumentException("Invalid note type"));

        mockMvc.perform(post("/api/v1/notes")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().is5xxServerError());
    }
}
