package com.fit.fitnessapp.workout;

import com.fit.fitnessapp.workout.adapter.out.parser.JefitCsvParserAdapter;
import com.fit.fitnessapp.workout.domain.WorkoutImportResult;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class JefitCsvParserAdapterTest {

    private final JefitCsvParserAdapter parser = new JefitCsvParserAdapter();

    @Test
    void supportsJefitImportFormatAliases() {
        assertThat(parser.supports("jefit")).isTrue();
        assertThat(parser.supports("jefit-csv")).isTrue();
    }

    @Test
    void malformedRowsDoNotStopImportAndExposeWarnings() {
        WorkoutImportResult result = parser.parse(csv("""
                ### WORKOUT SESSIONS
                _id,starttime
                1,1710000000
                broken,not-a-time
                ### EXERCISE LOGS
                _id,ename,belongsession
                10,Bench Press,1
                ### EXERCISE SET LOGS
                exercise_log_id,weight_lbs,reps,set_index
                10,225,5,1
                """));

        assertThat(result.importedCount()).isEqualTo(1);
        assertThat(result.skippedCount()).isEqualTo(1);
        assertThat(result.sessions()).hasSize(1);
        assertThat(result.sessions().getFirst().exercises()).hasSize(1);
        assertThat(result.sessions().getFirst().exercises().getFirst().sets()).hasSize(1);
        assertThat(result.warnings()).singleElement().satisfies(warning -> {
            assertThat(warning.section()).isEqualTo("WORKOUT SESSIONS");
            assertThat(warning.lineNumber()).isEqualTo(4);
            assertThat(warning.reason()).contains("invalid workout session number");
        });
    }

    @Test
    void parsesCurrentJefitSectionedExportShape() {
        WorkoutImportResult result = parser.parse(csv("""
                ### WORKOUT SESSIONS
                rowid,_id,USERID,edit_time,day_id,total_time,workout_time,rest_time,wasted_time,total_exercises,starttime,endtime
                1,1770220318,7,0,0,0,0,0,0,2,1770220318,1770223918
                ### EXERCISE LOGS
                USERID,TIMESTAMP,belongSys,logs,_id,record,mydate,eid,ename,day_item_id,belongsession,logTime,interval_logs,auto_generated
                7,0,0,"64.9998x5,64.9998x5",1,75.83,2026-02-04,8,Barbell Incline Bench Press,8,1770220318,0,,0
                7,0,0,"31.9998x8,31.9998x8",2,40.53,2026-02-04,45,Machine Fly,9,1770220318,0,,0
                ### EXERCISE SET LOGS
                _id,userid,exercise_log_id,set_index,weight_lbs,reps,calories,distance_mi,speed_mph,laps,duration
                1,7,1,0,143.2999,5,0,0,0,0,0
                2,7,1,1,143.2999,5,0,0,0,0,0
                3,7,2,0,70.5479,8,0,0,0,0,0
                4,7,2,1,70.5479,8,0,0,0,0,0
                """));

        assertThat(result.warnings()).isEmpty();
        assertThat(result.sessions()).hasSize(1);
        assertThat(result.sessions().getFirst().externalId()).isEqualTo(1770220318L);
        assertThat(result.sessions().getFirst().exercises()).hasSize(2);
        assertThat(result.sessions().getFirst().exercises().getFirst().sets()).hasSize(2);
    }

    @Test
    void parsesJefitCardioLogsAsWorkoutContext() {
        WorkoutImportResult result = parser.parse(csv("""
                ### CARDIO LOGS
                row_id,TIMESTAMP,USERID,_id,weeklyLogId,eid,belongSys,lap,duration,speed,distance,calorie,mydate
                23937347,"2026-03-20 11:34:02",13687197,2,0,321,1,0,3600,0,1.5,220,2026-03-20
                """));

        assertThat(result.warnings()).isEmpty();
        assertThat(result.sessions()).hasSize(1);
        assertThat(result.sessions().getFirst().externalId()).isEqualTo(23937347L);
        assertThat(result.sessions().getFirst().exercises()).isEmpty();
        assertThat(result.sessions().getFirst().cardioExercises())
                .singleElement()
                .satisfies(cardio -> {
                    assertThat(cardio.jefitId()).isEqualTo(23937347L);
                    assertThat(cardio.exerciseId()).isEqualTo(321L);
                    assertThat(cardio.durationSeconds()).isEqualTo(3600);
                    assertThat(cardio.distance()).isEqualTo(1.5);
                    assertThat(cardio.calories()).isEqualTo(220.0);
                });
    }

    private ByteArrayInputStream csv(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }
}
