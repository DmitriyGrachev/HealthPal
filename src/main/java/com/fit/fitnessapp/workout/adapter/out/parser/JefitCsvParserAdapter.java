package com.fit.fitnessapp.workout.adapter.out.parser;

import com.fit.fitnessapp.workout.application.port.out.WorkoutParserPort;
import com.fit.fitnessapp.workout.domain.Exercise;
import com.fit.fitnessapp.workout.domain.Set;
import com.fit.fitnessapp.workout.domain.WorkoutImportResult;
import com.fit.fitnessapp.workout.domain.WorkoutImportWarning;
import com.fit.fitnessapp.workout.domain.WorkoutSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Slf4j
@Component
public class JefitCsvParserAdapter implements WorkoutParserPort {

    @Override
    public boolean supports(String format) {
        return "jefit".equalsIgnoreCase(format) || "jefit-csv".equalsIgnoreCase(format);
    }

    @Override
    public WorkoutImportResult parse(InputStream stream) {
        log.debug("Starting Jefit CSV parse");
        Map<Long, TempWorkout> tempWorkouts = new HashMap<>();
        Map<Long, TempExercise> tempExercises = new HashMap<>();
        List<WorkoutImportWarning> warnings = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            String currentSection = "";
            String[] headers = null;
            String delimiter = ",";
            int lineNumber = 0;

            reader.mark(1);
            if (reader.read() != 0xFEFF) {
                reader.reset();
            }

            while ((line = reader.readLine()) != null) {
                lineNumber++;
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                if (line.startsWith("###")) {
                    currentSection = line.replace("#", "").trim();
                    headers = null;
                    continue;
                }

                if (headers == null) {
                    delimiter = line.contains(";") ? ";" : ",";
                    headers = cleanColumns(line.split(Pattern.quote(delimiter)));
                    continue;
                }

                String[] data = cleanColumns(line.split(
                        Pattern.quote(delimiter) + "(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)",
                        -1));
                parseRow(currentSection, headers, data, lineNumber, tempWorkouts, tempExercises, warnings);
            }
        } catch (IOException | RuntimeException e) {
            throw new RuntimeException("Parse error", e);
        }

        return WorkoutImportResult.from(buildDomainObjects(tempWorkouts), warnings);
    }

    private void parseRow(
            String currentSection,
            String[] headers,
            String[] data,
            int lineNumber,
            Map<Long, TempWorkout> tempWorkouts,
            Map<Long, TempExercise> tempExercises,
            List<WorkoutImportWarning> warnings) {
        try {
            if (currentSection.contains("WORKOUT SESSIONS")) {
                parseSession(currentSection, headers, data, lineNumber, tempWorkouts, warnings);
            } else if (currentSection.contains("EXERCISE LOGS") && !currentSection.contains("SET")) {
                parseExerciseLog(currentSection, headers, data, lineNumber, tempWorkouts, tempExercises, warnings);
            } else if (currentSection.contains("EXERCISE SET LOGS")) {
                parseSetLog(currentSection, headers, data, lineNumber, tempExercises, warnings);
            }
        } catch (RuntimeException e) {
            recordWarning(warnings, currentSection, lineNumber, "invalid row");
            log.warn(
                    "Skipped Jefit CSV row: section={}, line={}, reason={}, error={}",
                    currentSection,
                    lineNumber,
                    "invalid row",
                    e.getClass().getSimpleName());
        }
    }

    private void parseSession(
            String section,
            String[] headers,
            String[] data,
            int lineNumber,
            Map<Long, TempWorkout> tempWorkouts,
            List<WorkoutImportWarning> warnings) {
        int idIdx = findIndex(headers, "_id");
        if (idIdx == -1) {
            idIdx = findIndex(headers, "rowid");
        }

        int timeIdx = findIndex(headers, "starttime");
        if (timeIdx == -1) {
            timeIdx = findIndex(headers, "endtime");
        }

        if (!hasRequiredValues(data, idIdx, timeIdx)) {
            recordWarning(warnings, section, lineNumber, "missing required workout session columns");
            return;
        }

        try {
            Long logId = Long.parseLong(data[idIdx]);
            long timestamp = Long.parseLong(data[timeIdx]);
            if (timestamp < 10_000_000_000L) {
                timestamp *= 1000;
            }

            LocalDateTime date = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(timestamp),
                    ZoneId.of("Europe/Kiev"));
            tempWorkouts.put(logId, new TempWorkout(logId, date));
        } catch (NumberFormatException e) {
            recordWarning(warnings, section, lineNumber, "invalid workout session number");
        } catch (DateTimeException e) {
            recordWarning(warnings, section, lineNumber, "invalid workout session date");
        }
    }

    private void parseExerciseLog(
            String section,
            String[] headers,
            String[] data,
            int lineNumber,
            Map<Long, TempWorkout> tempWorkouts,
            Map<Long, TempExercise> tempExercises,
            List<WorkoutImportWarning> warnings) {
        int idIdx = findIndex(headers, "_id");
        int nameIdx = findIndex(headers, "ename");
        if (nameIdx == -1) {
            nameIdx = findIndex(headers, "exercise_name");
        }
        int sessionIdIdx = findIndex(headers, "belongsession");
        if (sessionIdIdx == -1) {
            sessionIdIdx = findIndex(headers, "workout_id");
        }

        if (!hasRequiredValues(data, idIdx, nameIdx, sessionIdIdx)) {
            recordWarning(warnings, section, lineNumber, "missing required exercise columns");
            return;
        }

        try {
            Long sessionId = Long.parseLong(data[sessionIdIdx]);
            TempWorkout workout = tempWorkouts.get(sessionId);
            if (workout == null) {
                recordWarning(warnings, section, lineNumber, "exercise references unknown workout session");
                return;
            }

            TempExercise exercise = new TempExercise(Long.parseLong(data[idIdx]), data[nameIdx]);
            workout.exercises.add(exercise);
            tempExercises.put(exercise.id, exercise);
        } catch (NumberFormatException e) {
            recordWarning(warnings, section, lineNumber, "invalid exercise number");
        }
    }

    private void parseSetLog(
            String section,
            String[] headers,
            String[] data,
            int lineNumber,
            Map<Long, TempExercise> tempExercises,
            List<WorkoutImportWarning> warnings) {
        int logIdIdx = findIndex(headers, "exercise_log_id");
        int weightIdx = findIndex(headers, "weight_lbs");
        int repsIdx = findIndex(headers, "reps");
        int idxIdx = findIndex(headers, "set_index");

        if (!hasRequiredValues(data, logIdIdx, weightIdx, repsIdx)) {
            recordWarning(warnings, section, lineNumber, "missing required set columns");
            return;
        }

        try {
            Long logId = Long.parseLong(data[logIdIdx]);
            TempExercise exercise = tempExercises.get(logId);
            if (exercise == null) {
                recordWarning(warnings, section, lineNumber, "set references unknown exercise log");
                return;
            }

            int setIndex = hasValue(data, idxIdx) ? Integer.parseInt(data[idxIdx]) : 0;
            int reps = hasValue(data, repsIdx) ? Integer.parseInt(data[repsIdx]) : 0;
            double weightLbs = hasValue(data, weightIdx) ? Double.parseDouble(data[weightIdx]) : 0;
            double weightKg = Math.round(weightLbs * 0.453592 * 10.0) / 10.0;
            exercise.sets.add(new Set(setIndex, reps, weightKg));
        } catch (NumberFormatException e) {
            recordWarning(warnings, section, lineNumber, "invalid set number");
        }
    }

    private List<WorkoutSession> buildDomainObjects(Map<Long, TempWorkout> tempWorkouts) {
        List<WorkoutSession> sessions = new ArrayList<>();
        for (TempWorkout workout : tempWorkouts.values()) {
            List<Exercise> domainExercises = new ArrayList<>();
            for (TempExercise exercise : workout.exercises) {
                if (!exercise.sets.isEmpty()) {
                    domainExercises.add(new Exercise(exercise.id, exercise.name, exercise.sets));
                }
            }
            if (!domainExercises.isEmpty()) {
                sessions.add(new WorkoutSession(workout.id, workout.date, domainExercises));
            }
        }
        return sessions;
    }

    private void recordWarning(
            List<WorkoutImportWarning> warnings,
            String section,
            int lineNumber,
            String reason) {
        warnings.add(new WorkoutImportWarning(section, lineNumber, reason));
        log.warn("Skipped Jefit CSV row: section={}, line={}, reason={}", section, lineNumber, reason);
    }

    private boolean hasRequiredValues(String[] data, int... indexes) {
        for (int index : indexes) {
            if (!hasValue(data, index)) {
                return false;
            }
        }
        return true;
    }

    private boolean hasValue(String[] data, int index) {
        return index >= 0 && data.length > index && !data[index].isBlank();
    }

    private String[] cleanColumns(String[] columns) {
        for (int i = 0; i < columns.length; i++) {
            columns[i] = columns[i].trim().replace("\"", "");
        }
        return columns;
    }

    private int findIndex(String[] headers, String colName) {
        for (int i = 0; i < headers.length; i++) {
            if (headers[i].equalsIgnoreCase(colName)) {
                return i;
            }
        }
        return -1;
    }

    private static class TempWorkout {
        private final Long id;
        private final LocalDateTime date;
        private final List<TempExercise> exercises = new ArrayList<>();

        private TempWorkout(Long id, LocalDateTime date) {
            this.id = id;
            this.date = date;
        }
    }

    private static class TempExercise {
        private final Long id;
        private final String name;
        private final List<Set> sets = new ArrayList<>();

        private TempExercise(Long id, String name) {
            this.id = id;
            this.name = name;
        }
    }
}
