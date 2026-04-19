package com.fit.fitnessapp.auth.adapter.in.web;

import com.fit.fitnessapp.auth.application.port.in.UserNoteUseCase;
import com.fit.fitnessapp.auth.domain.UserNoteDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
    
    @Operation(summary = "Create a user note")
    @PostMapping
    public ResponseEntity<UserNoteDto> createNote(@RequestBody UserNoteDto note) {
        UserNoteDto created = userNoteService.createNote(note);
        return ResponseEntity.ok(created);
    }
    
    @Operation(summary = "Get notes for user")
    @GetMapping
    public ResponseEntity<List<UserNoteDto>> getUserNotes(
            @RequestParam Long userId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
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
        userNoteService.deleteNote(noteId);
        return ResponseEntity.noContent().build();
    }
}
