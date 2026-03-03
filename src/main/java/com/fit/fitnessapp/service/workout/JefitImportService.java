package com.fit.fitnessapp.service.workout;

import com.fit.fitnessapp.model.workout.Workout;
import com.fit.fitnessapp.model.workout.WorkoutExercise;
import com.fit.fitnessapp.model.workout.WorkoutSet;
import com.fit.fitnessapp.repository.WorkoutRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import lombok.RequiredArgsConstructor;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class JefitImportService {

    private final WorkoutRepository workoutRepository;

    private Map<Long, Workout> tempWorkouts = new HashMap<>();
    private Map<Long, WorkoutExercise> tempExercises = new HashMap<>();

    @Transactional
    public void importJefitFile(MultipartFile file) {
        tempWorkouts.clear();
        tempExercises.clear();

        System.out.println(">>> НАЧАЛО ИМПОРТА ФАЙЛА: " + file.getOriginalFilename());

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            String currentSection = "";
            String[] headers = null;
            String delimiter = ","; // По умолчанию запятая

            // Удаляем BOM (невидимый символ в начале файла), если есть
            reader.mark(1);
            if (reader.read() != 0xFEFF) {
                reader.reset();
            }

            int lineNum = 0;
            while ((line = reader.readLine()) != null) {
                lineNum++;
                line = line.trim();

                if (line.isEmpty()) continue;

                // 1. Определение секции (игнорируем кучу решеток #######)
                if (line.startsWith("###")) {
                    currentSection = normalizeSectionName(line);
                    headers = null; // Сброс заголовков
                    System.out.println("--- Найдена секция: " + currentSection + " (строка " + lineNum + ") ---");
                    continue;
                }

                // 2. Чтение заголовков
                if (headers == null) {
                    // Пытаемся угадать разделитель по строке заголовков
                    if (line.contains(";")) delimiter = ";";
                    else delimiter = ",";

                    headers = line.split(delimiter);
                    // Чистим заголовки от пробелов и кавычек
                    for(int i=0; i<headers.length; i++) headers[i] = headers[i].trim().replace("\"", "");

                    System.out.println("Заголовки: " + Arrays.toString(headers));
                    continue;
                }

                // 3. Разбор данных
                String[] data = line.split(delimiter + "(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);

                // Очищаем данные от кавычек сразу
                for(int i=0; i<data.length; i++) {
                    data[i] = data[i].trim().replace("\"", "");
                }

                try {
                    if (currentSection.contains("WORKOUT SESSIONS")) {
                        parseSession(headers, data);
                    } else if (currentSection.contains("EXERCISE LOGS") && !currentSection.contains("SET")) {
                        parseExerciseLog(headers, data);
                    } else if (currentSection.contains("EXERCISE SET LOGS")) {
                        parseSetLog(headers, data);
                    }
                } catch (Exception e) {
                    System.err.println("Ошибка в строке " + lineNum + ": " + e.getMessage());
                }
            }

            if (tempWorkouts.isEmpty()) {
                System.out.println("!!! ВНИМАНИЕ: Ни одной тренировки не найдено. Проверь логи выше.");
            } else {
                workoutRepository.saveAll(tempWorkouts.values());
                System.out.println(">>> УСПЕШНО СОХРАНЕНО: " + tempWorkouts.size() + " тренировок.");
            }

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Критическая ошибка импорта: " + e.getMessage());
        }
    }

    // Убираем лишние # и пробелы
    private String normalizeSectionName(String line) {
        return line.replace("#", "").trim();
    }

    private void parseSession(String[] headers, String[] data) {
        int idIdx = findIndex(headers, "_id"); // Иногда id, иногда _id
        if (idIdx == -1) idIdx = findIndex(headers, "rowid"); // Jefit иногда меняет названия

        int timeIdx = findIndex(headers, "TIMESTAMP");
        if (timeIdx == -1) timeIdx = findIndex(headers, "starttime"); // Запасной вариант

        if (idIdx == -1 || timeIdx == -1) {
            // System.out.println("Пропуск строки сессии: не найден ID или TIMESTAMP");
            return;
        }

        try {
            Workout workout = new Workout();
            workout.setJefitId(Long.parseLong(data[idIdx]));

            String dateStr = data[timeIdx];
            // Обработка формата даты (бывает разный)
            if (dateStr.length() > 19) dateStr = dateStr.substring(0, 19); // Обрезаем миллисекунды если есть
            workout.setDate(LocalDateTime.parse(dateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

            tempWorkouts.put(workout.getJefitId(), workout);
            System.out.println("parsed workout: " + workout.getDate());
        } catch (Exception e) {
            System.err.println("Ошибка парсинга даты/ID сессии: " + Arrays.toString(data));
        }
    }

    private void parseExerciseLog(String[] headers, String[] data) {
        int idIdx = findIndex(headers, "_id");
        int nameIdx = findIndex(headers, "ename"); // exercise name
        int sessionIdIdx = findIndex(headers, "belongsession"); // ID тренировки

        if (idIdx == -1 || nameIdx == -1 || sessionIdIdx == -1) return;

        try {
            Long sessionId = Long.parseLong(data[sessionIdIdx]);
            Workout workout = tempWorkouts.get(sessionId);

            if (workout != null) {
                WorkoutExercise ex = new WorkoutExercise();
                ex.setJefitLogId(Long.parseLong(data[idIdx]));
                ex.setExerciseName(data[nameIdx]);
                ex.setWorkout(workout);

                workout.getExercises().add(ex);
                tempExercises.put(ex.getJefitLogId(), ex);
            }
        } catch (NumberFormatException e) {
            // игнор битых строк
        }
    }

    private void parseSetLog(String[] headers, String[] data) {
        int logIdIdx = findIndex(headers, "exercise_log_id");
        int weightIdx = findIndex(headers, "weight_lbs");
        int repsIdx = findIndex(headers, "reps");
        int idxIdx = findIndex(headers, "set_index");

        if (logIdIdx == -1 || weightIdx == -1 || repsIdx == -1) return;

        try {
            Long logId = Long.parseLong(data[logIdIdx]);
            WorkoutExercise exercise = tempExercises.get(logId);

            if (exercise != null) {
                WorkoutSet set = new WorkoutSet();

                // Проверка на пустоту
                String idxVal = (idxIdx != -1 && !data[idxIdx].isEmpty()) ? data[idxIdx] : "0";
                set.setSetIndex(Integer.parseInt(idxVal));

                String repsVal = !data[repsIdx].isEmpty() ? data[repsIdx] : "0";
                set.setReps(Integer.parseInt(repsVal));

                String wVal = !data[weightIdx].isEmpty() ? data[weightIdx] : "0";
                double weightLbs = Double.parseDouble(wVal);
                set.setWeight(Math.round(weightLbs * 0.453592 * 10.0) / 10.0);

                set.setExercise(exercise);
                exercise.getSets().add(set);
            }
        } catch (Exception e) {
            // System.err.println("Ошибка парсинга сета: " + e.getMessage());
        }
    }

    private int findIndex(String[] headers, String colName) {
        for (int i = 0; i < headers.length; i++) {
            // Игнорируем регистр
            if (headers[i].equalsIgnoreCase(colName)) return i;
        }
        return -1;
    }
}