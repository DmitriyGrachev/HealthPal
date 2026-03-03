package com.fit.fitnessapp.controller;

import com.fit.fitnessapp.security.CurrentUserService;
import com.fit.fitnessapp.service.diet.FatSecretService;
import com.github.scribejava.core.model.Response;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDate;

@RestController
@RequestMapping("/fatsecret")
@RequiredArgsConstructor
public class FatSecretAuthController {

    private final FatSecretService fatsecretService;
    private final CurrentUserService currentUserService;

    @GetMapping("/login")
    public ResponseEntity<?> login(HttpServletResponse response) throws IOException {
        try {
            Long userId = currentUserService.getCurrentUserId();
            String authUrl = fatsecretService.getAuthorizationUrl(userId);
            return ResponseEntity.ok(authUrl);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("Error: " + e.getMessage());
        }
    }

    @GetMapping("/callback")
    public ResponseEntity<String> callback(
            @RequestParam("oauth_token") String oauthToken,
            @RequestParam("oauth_verifier") String oauthVerifier
    ) {
        try {
            Long userId = fatsecretService.processCallback(oauthToken, oauthVerifier);
            return ResponseEntity.ok("Connected successfully for user " + userId + "! Try /fatsecret/test");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    //TODO TEST

    /**
     * Returns profile of user
     * @return Profile
     */
    @GetMapping("/test")
    public ResponseEntity<String> testApi() {
        return executeApiCall("profile.get", null);
    }
    /**
     * Returns TODAY food entries (meals)
     * @return Food entries
     */
    @GetMapping("/today-meals")
    public ResponseEntity<String> testFoodApi() {
        long date = System.currentTimeMillis() / 86400000L;
        return executeApiCall("food_entries.get", String.valueOf(date));
    }

    /**
     * Returns TODAY food entries (meals)
     * @return Food entries
     */
    @GetMapping("/all-entries-day")
    public ResponseEntity<String> testGetAllEntriesDay() {
        long date = System.currentTimeMillis() / 86400000L;
        return executeApiCall("food_entries.get.v2", String.valueOf(date));
    }

    //TODO currently minus month, but need workaround with request , so that user can enter month
    @GetMapping("/all-entries-month")
    public ResponseEntity<String> testGetAllEntriesMonth(@RequestParam(defaultValue = "30") String month) {
        long dateInt = LocalDate.now().toEpochDay();
        return executeApiCall("food_entries.get_month.v2", String.valueOf(dateInt - Integer.parseInt(month)));
    }
    @GetMapping("/all-entries-current-month")
    public ResponseEntity<String> testGetAllEntriesCurrentMonth(){
        long dateInt = LocalDate.now().toEpochDay();
        return executeApiCall("food_entries.get_month.v2", String.valueOf(dateInt));

    }

    // Вспомогательный метод
    private ResponseEntity<String> executeApiCall(String method, String date) {
        try {
            Long userId = currentUserService.getCurrentUserId();
            Response response = fatsecretService.makeApiCall(userId, method, date);
            return ResponseEntity.ok()
                    .header("Content-Type", "application/json")
                    .body(response.getBody());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(401).body("Not connected. Please /login first.");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("API Error: " + e.getMessage());
        }
    }
}