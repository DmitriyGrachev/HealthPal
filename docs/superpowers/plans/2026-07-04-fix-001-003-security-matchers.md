# FIX-001-003 Security Matchers Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Protect the all-user weekly/monthly report endpoints with `ADMIN` and protect the real workout import endpoint with `VIP`.

**Architecture:** Keep the fix in the auth/security boundary by changing only Spring Security matchers. Prove the matchers against the real controller paths with a focused `@WebMvcTest` that imports the existing security chain and mocks controller dependencies. Do not change analytics or workout business logic.

**Tech Stack:** Java 21, Spring Boot WebMvcTest, Spring Security, MockMvc, JUnit 5, Mockito, Maven Surefire.

---

## File Structure

- Create: `src/test/java/com/fit/fitnessapp/auth/SecurityProtectedEndpointsWebTest.java`
  - Focused web/security regression test for FIX-001, FIX-002, and FIX-003.
  - Uses real controllers:
    - `WeeklyReportController`
    - `MonthlyReportController`
    - `WorkoutImportController`
  - Mocks orchestrators/use cases so tests prove security routing without running batch/report/import work.

- Modify: `src/main/java/com/fit/fitnessapp/auth/infrastructure/config/SecurityConfig.java`
  - Add exact `ADMIN` matchers for `GET /api/v1/week` and `GET /api/v1/month`.
  - Replace stale `/api/import/**` matcher with real `/api/v1/workout-import/**` matcher.

- Do not modify:
  - `WeeklyReportController`
  - `MonthlyReportController`
  - `WorkoutImportController`
  - `WeeklyReportOrchestrator`
  - `MonthlyReportOrchestrator`
  - `ImportWorkoutUseCase`

---

### Task 1: Add Failing Security Tests

**Files:**
- Create: `src/test/java/com/fit/fitnessapp/auth/SecurityProtectedEndpointsWebTest.java`

- [ ] **Step 1: Create the focused web/security test class**

Add this complete file:

```java
package com.fit.fitnessapp.auth;

import com.fit.fitnessapp.ai.RateLimitInterceptor;
import com.fit.fitnessapp.analytics.application.MonthlyReportOrchestrator;
import com.fit.fitnessapp.analytics.application.WeeklyReportOrchestrator;
import com.fit.fitnessapp.analytics.port.in.MonthlyReportController;
import com.fit.fitnessapp.analytics.port.in.WeeklyReportController;
import com.fit.fitnessapp.auth.application.service.UserDetailsService;
import com.fit.fitnessapp.auth.infrastructure.config.SecurityConfig;
import com.fit.fitnessapp.auth.infrastructure.config.SecurityProperties;
import com.fit.fitnessapp.auth.infrastructure.utils.JwtCore;
import com.fit.fitnessapp.auth.infrastructure.utils.TokenFilter;
import com.fit.fitnessapp.exception.ApiErrorResponseWriter;
import com.fit.fitnessapp.workout.adapter.in.web.WorkoutImportController;
import com.fit.fitnessapp.workout.application.port.in.ImportWorkoutUseCase;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        WeeklyReportController.class,
        MonthlyReportController.class,
        WorkoutImportController.class
})
@Import({
        SecurityConfig.class,
        TokenFilter.class,
        JwtCore.class,
        ApiErrorResponseWriter.class
})
@EnableConfigurationProperties(SecurityProperties.class)
@TestPropertySource(properties = {
        "fitness.app.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "fitness.app.jwt-expiration=PT1H",
        "fitness.app.cors.allowed-origins=http://localhost:3000"
})
class SecurityProtectedEndpointsWebTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private UserDetailsService userDetailsService;
    @MockitoBean private RateLimitInterceptor rateLimitInterceptor;
    @MockitoBean private WeeklyReportOrchestrator weeklyReportOrchestrator;
    @MockitoBean private MonthlyReportOrchestrator monthlyReportOrchestrator;
    @MockitoBean private ImportWorkoutUseCase importWorkoutUseCase;
    @MockitoBean private CurrentUserApi currentUserApi;

    @Test
    void weeklyReportBatchEndpointIsAdminOnly() throws Exception {
        mockMvc.perform(get("/api/v1/week"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.status").value(401));

        stubUser("user", "USER");

        mockMvc.perform(get("/api/v1/week")
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor("user")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.status").value(403));

        verifyNoInteractions(weeklyReportOrchestrator);

        stubUser("admin", "ADMIN");

        mockMvc.perform(get("/api/v1/week")
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor("admin")))
                .andExpect(status().isOk());

        verify(weeklyReportOrchestrator).generateWeeklyReports();
    }

    @Test
    void monthlyReportBatchEndpointIsAdminOnly() throws Exception {
        mockMvc.perform(get("/api/v1/month"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.status").value(401));

        stubUser("user", "USER");

        mockMvc.perform(get("/api/v1/month")
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor("user")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.status").value(403));

        verifyNoInteractions(monthlyReportOrchestrator);

        stubUser("admin", "ADMIN");

        mockMvc.perform(get("/api/v1/month")
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor("admin")))
                .andExpect(status().isOk());

        verify(monthlyReportOrchestrator).generateMonthlyReports();
    }

    @Test
    void workoutImportEndpointIsVipOnly() throws Exception {
        mockMvc.perform(multipart("/api/v1/workout-import/import/jefit")
                        .file(workoutFile()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.status").value(401));

        stubUser("user", "USER");

        mockMvc.perform(multipart("/api/v1/workout-import/import/jefit")
                        .file(workoutFile())
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor("user")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.status").value(403));

        verifyNoInteractions(currentUserApi, importWorkoutUseCase);

        stubUser("vip", "VIP");
        when(currentUserApi.getCurrentUserId()).thenReturn(42L);

        mockMvc.perform(multipart("/api/v1/workout-import/import/jefit")
                        .file(workoutFile())
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor("vip")))
                .andExpect(status().isOk());

        verify(currentUserApi).getCurrentUserId();
        verify(importWorkoutUseCase).importWorkouts(any(InputStream.class), eq("jefit"), eq(42L));
    }

    @Test
    void staleWorkoutImportMatcherDoesNotExposeOldPath() throws Exception {
        stubUser("vip", "VIP");

        mockMvc.perform(multipart("/api/import/jefit")
                        .file(workoutFile())
                        .header(HttpHeaders.AUTHORIZATION, bearerTokenFor("vip")))
                .andExpect(status().isNotFound());

        verify(importWorkoutUseCase, never()).importWorkouts(any(InputStream.class), eq("jefit"), eq(42L));
    }

    private void stubUser(String username, String... roles) {
        when(userDetailsService.loadUserByUsername(username))
                .thenReturn(User.withUsername(username).password("n/a").roles(roles).build());
    }

    private String bearerTokenFor(String subject) {
        return "Bearer " + tokenFor(subject, 3_600_000);
    }

    private String tokenFor(String subject, long expiresInMillis) {
        Date now = new Date();
        return Jwts.builder()
                .setSubject(subject)
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + expiresInMillis))
                .signWith(
                        Keys.hmacShaKeyFor(Decoders.BASE64.decode("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")),
                        SignatureAlgorithm.HS256)
                .compact();
    }

    private MockMultipartFile workoutFile() {
        return new MockMultipartFile(
                "file",
                "workouts.csv",
                "text/csv",
                "Date,Exercise,Reps\n2026-07-04,Squat,5\n".getBytes(StandardCharsets.UTF_8));
    }
}
```

- [ ] **Step 2: Run the focused test and verify it fails for the right reason**

Run:

```powershell
mvn "-Dtest=SecurityProtectedEndpointsWebTest" test
```

Expected before production changes:

```text
Tests run: 4, Failures: 3, Errors: 0, Skipped: 0
```

The expected failing assertions are:

```text
weeklyReportBatchEndpointIsAdminOnly: expected 403 but was 200
monthlyReportBatchEndpointIsAdminOnly: expected 403 but was 200
workoutImportEndpointIsVipOnly: expected 403 but was 200
```

If the test class does not compile, fix only imports or method signatures in this new test file. Do not change production code until the failing security behavior is reproduced.

---

### Task 2: Update Security Matchers

**Files:**
- Modify: `src/main/java/com/fit/fitnessapp/auth/infrastructure/config/SecurityConfig.java`

- [ ] **Step 1: Replace the authorize rules block**

In `SecurityConfig.filterChain`, replace the current `authorizeHttpRequests` matcher section with this block:

```java
                .authorizeHttpRequests(auth -> auth
                        // All-user report generation is an operational batch action.
                        .requestMatchers(HttpMethod.GET, "/api/v1/week", "/api/v1/month").hasRole("ADMIN")
                        // File import API is limited to VIP users because it can mutate workout data in bulk.
                        .requestMatchers("/api/v1/workout-import/**").hasRole("VIP")
                        // Dev/test operational endpoints are restricted to administrators.
                        .requestMatchers("/test/**").hasRole("ADMIN")
                        // FatSecret redirects users here without an application JWT.
                        .requestMatchers("/api/v1/nutrition/callback").permitAll()
                        // Preflight requests are handled by the configured CORS policy before auth.
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // Authentication endpoints must stay public so users can register and obtain JWTs.
                        .requestMatchers("/auth/**").permitAll()
                        // Every other API requires a valid JWT.
                        .anyRequest().authenticated()
                )
```

This intentionally removes the stale `/api/import/**` matcher because the real controller path is `/api/v1/workout-import/import/{format}`.

- [ ] **Step 2: Check the matcher text directly**

Run:

```powershell
rg -n "/api/v1/week|/api/v1/month|/api/v1/workout-import|/api/import" src/main/java/com/fit/fitnessapp/auth/infrastructure/config/SecurityConfig.java
```

Expected:

```text
src/main/java/com/fit/fitnessapp/auth/infrastructure/config/SecurityConfig.java:<line>:                        .requestMatchers(HttpMethod.GET, "/api/v1/week", "/api/v1/month").hasRole("ADMIN")
src/main/java/com/fit/fitnessapp/auth/infrastructure/config/SecurityConfig.java:<line>:                        .requestMatchers("/api/v1/workout-import/**").hasRole("VIP")
```

There should be no `/api/import/**` line.

---

### Task 3: Run Narrow Verification

**Files:**
- Test: `src/test/java/com/fit/fitnessapp/auth/SecurityProtectedEndpointsWebTest.java`
- Test: `src/test/java/com/fit/fitnessapp/auth/SecurityConfigWebTest.java`

- [ ] **Step 1: Run the new focused regression test**

Run:

```powershell
mvn "-Dtest=SecurityProtectedEndpointsWebTest" test
```

Expected:

```text
Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

- [ ] **Step 2: Run the existing security config test**

Run:

```powershell
mvn "-Dtest=SecurityConfigWebTest" test
```

Expected:

```text
Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

If `SecurityConfigWebTest` has a different test count because more tests were added on the branch, the required result is still `Failures: 0, Errors: 0` and `BUILD SUCCESS`.

---

### Task 4: Run Full Default Gate

**Files:**
- No additional file edits.

- [ ] **Step 1: Run the project default gate**

Run:

```powershell
mvn test
```

Expected:

```text
BUILD SUCCESS
```

The default gate is the right broad check because this change affects normal Spring Security behavior and default `*Test` web tests. Do not run `mvn verify -Pintegration`; these changes do not touch PostgreSQL, Flyway, JPA mappings, or pgvector. Do not run `mvn test -Parchitecture`; these changes do not touch Spring Modulith module boundaries.

---

### Task 5: Backlog Closeout

**Files:**
- Modify: `BACKLOG.md`

- [ ] **Step 1: Mark FIX-001, FIX-002, and FIX-003 complete only after `mvn test` succeeds**

Change the top checklist entries for the three fixed items from unchecked to checked while preserving the existing text encoding:

```markdown
- [x] [FIX-001]
- [x] [FIX-002]
- [x] [FIX-003]
```

Do not mark FIX-004 or later items. Do not re-encode the file as part of this security matcher fix.

- [ ] **Step 2: Inspect the final diff**

Run:

```powershell
git diff -- src/test/java/com/fit/fitnessapp/auth/SecurityProtectedEndpointsWebTest.java src/main/java/com/fit/fitnessapp/auth/infrastructure/config/SecurityConfig.java BACKLOG.md
```

Expected diff shape:

```text
new focused web/security test file
SecurityConfig matcher block changed
three BACKLOG.md checklist entries changed from [ ] to [x]
```

Do not stage or commit unless the user explicitly asks.

---

## Acceptance Mapping

- FIX-001 `GET /api/v1/week`
  - Anonymous request returns `401`: `weeklyReportBatchEndpointIsAdminOnly`
  - `USER` request returns `403`: `weeklyReportBatchEndpointIsAdminOnly`
  - `ADMIN` request returns `200`: `weeklyReportBatchEndpointIsAdminOnly`
  - `WeeklyReportOrchestrator.generateWeeklyReports()` is called once for `ADMIN`: `weeklyReportBatchEndpointIsAdminOnly`

- FIX-002 `GET /api/v1/month`
  - Anonymous request returns `401`: `monthlyReportBatchEndpointIsAdminOnly`
  - `USER` request returns `403`: `monthlyReportBatchEndpointIsAdminOnly`
  - `ADMIN` request returns `200`: `monthlyReportBatchEndpointIsAdminOnly`
  - `MonthlyReportOrchestrator.generateMonthlyReports()` is called once for `ADMIN`: `monthlyReportBatchEndpointIsAdminOnly`

- FIX-003 `POST /api/v1/workout-import/import/jefit`
  - Anonymous request returns `401`: `workoutImportEndpointIsVipOnly`
  - `USER` request returns `403`: `workoutImportEndpointIsVipOnly`
  - `VIP` request passes the security layer and calls the import use case once: `workoutImportEndpointIsVipOnly`
  - `SecurityConfig` uses `/api/v1/workout-import/**`, not stale `/api/import/**`: `rg` check in Task 2

---

## Risks And Guardrails

- Matcher order matters. Keep the specific admin/VIP matchers before `.anyRequest().authenticated()`.
- Keep `OPTIONS /**` permitted so CORS preflight behavior does not regress.
- Do not introduce role hierarchy in this fix. `ADMIN` protects all-user analytics generation, and `VIP` protects workout import exactly as the backlog specifies.
- Do not test localized response messages. Assert stable status and error code fields only.
- Do not run real analytics generation, workout import parsing, OpenRouter, Gemini, Telegram, FatSecret, PostgreSQL, or pgvector in these tests.
- Do not update Spring Security, Spring Boot, JJWT, or Spring AI dependencies in this fix.

---

## Self-Review

- Spec coverage: FIX-001, FIX-002, and FIX-003 each map to a concrete test method and one matcher change.
- Placeholder scan: The plan contains no placeholder markers, no unspecified handlers, and no deferred implementation steps.
- Type consistency: Controller dependency names and methods match current production code:
  - `WeeklyReportOrchestrator.generateWeeklyReports()`
  - `MonthlyReportOrchestrator.generateMonthlyReports()`
  - `CurrentUserApi.getCurrentUserId()`
  - `ImportWorkoutUseCase.importWorkouts(InputStream, String, Long)`

---

## Execution Handoff

Plan complete. Use either execution mode:

1. Subagent-Driven: implement one task at a time with a fresh subagent and review between tasks.
2. Inline Execution: implement tasks in this session with checkpoints after the failing test, matcher fix, narrow gate, and full gate.
