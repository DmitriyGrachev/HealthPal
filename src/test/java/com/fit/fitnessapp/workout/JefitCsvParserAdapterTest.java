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

    private ByteArrayInputStream csv(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }
}
