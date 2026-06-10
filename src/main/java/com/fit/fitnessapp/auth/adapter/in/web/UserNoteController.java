package com.fit.fitnessapp.auth.adapter.in.web;

import com.fit.fitnessapp.auth.CurrentUserApi;
import com.fit.fitnessapp.auth.application.port.in.UserNoteUseCase;
import com.fit.fitnessapp.auth.domain.UserNoteDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/notes")
@RequiredArgsConstructor
@Tag(name = "User Notes", description = "User notes management")
public class UserNoteController {
    
    private final UserNoteUseCase userNoteService;
    private final CurrentUserApi currentUserApi;
    
    @Operation(summary = "Create a user note")
    @PostMapping
    public ResponseEntity<UserNoteDto> createNote(@Valid @RequestBody UserNoteDto note) {
        Long userId = currentUserApi.getCurrentUserId();
        UserNoteDto scopedNote = new UserNoteDto(
                note.id(),
                userId,
                note.relatedDate(),
                note.content(),
                note.type()
        );
        UserNoteDto created = userNoteService.createNote(scopedNote);
        return ResponseEntity.ok(created);
    }
    
    @Operation(summary = "Get notes for user")
    @GetMapping
    public ResponseEntity<List<UserNoteDto>> getUserNotes(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        Long userId = currentUserApi.getCurrentUserId();
        List<UserNoteDto> notes;
        if (from != null && to != null) {
            notes = userNoteService.getNotesByUserIdAndDateRange(userId, from, to);
        } else {
            notes = userNoteService.getNotesByUserId(userId);
        }
        return ResponseEntity.ok(notes);
    }
    
    @Operation(summary = "Delete a note")
    @DeleteMapping("/{noteId}")
    public ResponseEntity<Void> deleteNote(@PathVariable Long noteId) {
        Long userId = currentUserApi.getCurrentUserId();
        userNoteService.deleteNote(userId, noteId);
        return ResponseEntity.noContent().build();
    }
}
