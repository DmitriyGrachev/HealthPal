package com.fit.fitnessapp.workout.adapter.out.parser;

import com.fit.fitnessapp.workout.application.port.out.WorkoutParserPort;
import com.fit.fitnessapp.workout.domain.Exercise;
import com.fit.fitnessapp.workout.domain.Set;
import com.fit.fitnessapp.workout.domain.WorkoutSession;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
public class JefitCsvParserAdapter implements WorkoutParserPort {

    @Override
    public boolean supports(String format) {
        return "jefit-csv".equalsIgnoreCase(format);
    }

    @Override
    public List<WorkoutSession> parse(InputStream stream) {
        Map<Long, TempWorkout> tempWorkouts = new HashMap<>();
        Map<Long, TempExercise> tempExercises = new HashMap<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            String currentSection = "";
            String[] headers = null;
            String delimiter = ",";

            reader.mark(1);
            if (reader.read() != 0xFEFF) reader.reset();

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                // 1. Твоя родная логика определения секции
                if (line.startsWith("###")) {
                    currentSection = line.replace("#", "").trim();
                    headers = null;
                    continue;
                }

                // 2. Твоя логика заголовков
                if (headers == null) {
                    if (line.contains(";")) delimiter = ";";
                    else delimiter = ",";

                    headers = line.split(delimiter);
                    for (int i = 0; i < headers.length; i++) headers[i] = headers[i].trim().replace("\"", "");
                    continue;
                }

                // 3. Твоя логика разделения строки
                String[] data = line.split(delimiter + "(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);
                for (int i = 0; i < data.length; i++) data[i] = data[i].trim().replace("\"", "");

                try {
                    if (currentSection.contains("WORKOUT SESSIONS")) {
                        parseSession(headers, data, tempWorkouts);
                    } else if (currentSection.contains("EXERCISE LOGS") && !currentSection.contains("SET")) {
                        parseExerciseLog(headers, data, tempWorkouts, tempExercises);
                    } else if (currentSection.contains("EXERCISE SET LOGS")) {
                        parseSetLog(headers, data, tempExercises);
                    }
                } catch (Exception ignored) {
                    // Игнорируем битые строки, как в старом коде
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Parse error", e);
        }

        // Перекладываем во временных DTO в чистый домен
        return buildDomainObjects(tempWorkouts);
    }

    private void parseSession(String[] headers, String[] data, Map<Long, TempWorkout> tempWorkouts) {
        int idIdx = findIndex(headers, "_id");
        if (idIdx == -1) idIdx = findIndex(headers, "rowid");

        int timeIdx = findIndex(headers, "TIMESTAMP");
        if (timeIdx == -1) timeIdx = findIndex(headers, "starttime");

        if (idIdx == -1 || timeIdx == -1 || data.length <= timeIdx) return;

        try {
            Long logId = Long.parseLong(data[idIdx]);
            String dateStr = data[timeIdx];
            if (dateStr.length() > 19) dateStr = dateStr.substring(0, 19);
            LocalDateTime date = LocalDateTime.parse(dateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

            tempWorkouts.put(logId, new TempWorkout(logId, date));
        } catch (Exception ignored) {}
    }

    private void parseExerciseLog(String[] headers, String[] data, Map<Long, TempWorkout> tempWorkouts, Map<Long, TempExercise> tempExercises) {
        int idIdx = findIndex(headers, "_id");
        int nameIdx = findIndex(headers, "ename");
        if (nameIdx == -1) nameIdx = findIndex(headers, "exercise_name");
        int sessionIdIdx = findIndex(headers, "belongsession");
        if (sessionIdIdx == -1) sessionIdIdx = findIndex(headers, "workout_id");

        if (idIdx == -1 || nameIdx == -1 || sessionIdIdx == -1 || data.length <= sessionIdIdx) return;

        try {
            Long sessionId = Long.parseLong(data[sessionIdIdx]);
            TempWorkout workout = tempWorkouts.get(sessionId);

            if (workout != null) {
                TempExercise ex = new TempExercise(Long.parseLong(data[idIdx]), data[nameIdx]);
                workout.exercises.add(ex);
                tempExercises.put(ex.id, ex);
            }
        } catch (Exception ignored) {}
    }

    private void parseSetLog(String[] headers, String[] data, Map<Long, TempExercise> tempExercises) {
        int logIdIdx = findIndex(headers, "exercise_log_id");
        int weightIdx = findIndex(headers, "weight_lbs");
        int repsIdx = findIndex(headers, "reps");
        int idxIdx = findIndex(headers, "set_index");

        if (logIdIdx == -1 || weightIdx == -1 || repsIdx == -1 || data.length <= idxIdx) return;

        try {
            Long logId = Long.parseLong(data[logIdIdx]);
            TempExercise exercise = tempExercises.get(logId);

            if (exercise != null) {
                int setIndex = (idxIdx != -1 && !data[idxIdx].isEmpty()) ? Integer.parseInt(data[idxIdx]) : 0;
                int reps = !data[repsIdx].isEmpty() ? Integer.parseInt(data[repsIdx]) : 0;
                double weightLbs = !data[weightIdx].isEmpty() ? Double.parseDouble(data[weightIdx]) : 0;
                double weightKg = Math.round(weightLbs * 0.453592 * 10.0) / 10.0;

                exercise.sets.add(new Set(setIndex, reps, weightKg));
            }
        } catch (Exception ignored) {}
    }

    private List<WorkoutSession> buildDomainObjects(Map<Long, TempWorkout> tempWorkouts) {
        List<WorkoutSession> sessions = new ArrayList<>();
        for (TempWorkout tw : tempWorkouts.values()) {
            List<Exercise> domainExercises = new ArrayList<>();
            for (TempExercise te : tw.exercises) {
                if (!te.sets.isEmpty()) {
                    domainExercises.add(new Exercise(te.name, te.sets));
                }
            }
            if (!domainExercises.isEmpty()) {
                sessions.add(new WorkoutSession(tw.id, tw.date, domainExercises));
            }
        }
        return sessions;
    }

    private int findIndex(String[] headers, String colName) {
        for (int i = 0; i < headers.length; i++) {
            if (headers[i].equalsIgnoreCase(colName)) return i;
        }
        return -1;
    }

    private static class TempWorkout {
        Long id;
        LocalDateTime date;
        List<TempExercise> exercises = new ArrayList<>();
        TempWorkout(Long id, LocalDateTime date) { this.id = id; this.date = date; }
    }
    private static class TempExercise {
        Long id;
        String name;
        List<Set> sets = new ArrayList<>();
        TempExercise(Long id, String name) { this.id = id; this.name = name; }
    }
}