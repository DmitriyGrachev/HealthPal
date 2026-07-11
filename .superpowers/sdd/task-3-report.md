# Task 3 Report: FND-002 canonical registration and race-safe conflicts

## Delivered

- `RegisterService` normalizes the username with `trim()` and the email with `trim().toLowerCase(Locale.ROOT)` once at the application boundary; it forwards the password unchanged.
- `UserPersistenceAdapter` retains the username/email duplicate pre-checks, persists through `saveAndFlush`, and translates a flush-time `DataIntegrityViolationException` into `UserAlreadyExistsException`.
- `User` maps the planned constraints: username required/unique/64, email required/unique/255, password required/255.
- Duplicate registration is now an HTTP 409 response while retaining `USER_ALREADY_EXISTS`.
- Auth web validation covers 65-character usernames and 256-character emails.

## Files

- `src/main/java/com/fit/fitnessapp/auth/application/service/RegisterService.java`
- `src/main/java/com/fit/fitnessapp/auth/adapter/out/persistence/UserPersistenceAdapter.java`
- `src/main/java/com/fit/fitnessapp/auth/adapter/out/persistence/entity/user/User.java`
- `src/main/java/com/fit/fitnessapp/exception/GlobalExceptionHandler.java`
- `src/test/java/com/fit/fitnessapp/auth/application/service/RegisterServiceTest.java`
- `src/test/java/com/fit/fitnessapp/auth/adapter/out/persistence/UserPersistenceAdapterTest.java`
- `src/test/java/com/fit/fitnessapp/auth/AuthControllerTest.java`

## TDD evidence

### RED

Command:

```powershell
mvn "-Dtest=RegisterServiceTest,UserPersistenceAdapterTest,AuthControllerTest" test
```

Result: expected failure, 12 tests run with 4 failures.

- `RegisterServiceTest` received untrimmed username/email instead of canonical values.
- `UserPersistenceAdapterTest` observed `save` rather than `saveAndFlush`; its configured flush-time duplicate violation was not translated.
- `AuthControllerTest` expected 409 but received 400.

### GREEN

Command:

```powershell
mvn "-Dtest=RegisterServiceTest,UserPersistenceAdapterTest,AuthControllerTest" test
```

Result: 12 tests run, 0 failures, 0 errors.

### Full default gate

Command:

```powershell
mvn test
```

Result: 208 tests run, 0 failures, 0 errors, 0 skipped; `BUILD SUCCESS`.

No Docker/Testcontainers integration command was run because Docker is unavailable and this task is explicitly the unit/MockMvc portion.

## Self-review

- Confirmed username remains case-sensitive; no username lowercasing was introduced.
- Confirmed password is passed unmodified by `RegisterService` and only encoded by the persistence adapter.
- Confirmed existing duplicate pre-checks remain before persistence.
- Confirmed no Flyway migration was changed.
- `git diff --check` completed without whitespace errors.
- Rebuilt the repository Graphify graph as directed. The graph rebuild reported one pre-existing tooling limitation: PowerShell AST extraction is unavailable for `.codex/hooks/privacy-scan.ps1` because `tree-sitter-powershell` is not installed.

## Commit

Commit: this report is committed with the Task 3 change; use `git log -1` for the immutable commit ID.

## Concerns

None for the unit/MockMvc scope. The actual PostgreSQL unique-constraint behavior remains for Task 4/integration validation when Docker is available.
