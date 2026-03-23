package com.fit.fitnessapp.jdbc.workoout;

import com.fit.fitnessapp.auth.adapter.out.persistence.entity.user.User;
import com.fit.fitnessapp.auth.adapter.out.persistence.repository.UserRepository;
import com.fit.fitnessapp.workout.adapter.out.persistence.entity.WorkoutExerciseJpaEntity;
import com.fit.fitnessapp.workout.adapter.out.persistence.entity.WorkoutJpaEntity;
import com.fit.fitnessapp.workout.adapter.out.persistence.entity.WorkoutSetJpaEntity;
import com.fit.fitnessapp.workout.adapter.out.persistence.repository.WorkoutJpaRepository;
import com.fit.fitnessapp.workout.application.infrastructure.WorkoutSummaryWeeklyDto;
import com.fit.fitnessapp.workout.application.port.in.WorkoutQueryUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
public class WorkoutQueryUseCaseTest {

    @Autowired
    private WorkoutQueryUseCase workoutQueryUseCase;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private WorkoutJpaRepository workoutJpaRepository;

    private User testUser;
    private WorkoutJpaEntity testWorkout;

    @BeforeEach
    void setUp(){
        testUser = new User();
        testUser.setUsername("testuser");
        testUser.setEmail("test@example.com");
        testUser.setPassword("password123");
        testUser = userRepository.save(testUser);

        testWorkout = new WorkoutJpaEntity();
        testWorkout.setJefitId(1770220318L);
        testWorkout.setDate(LocalDateTime.of(2026, 3, 21, 10, 30, 0));
        testWorkout.setUserId(testUser.getId());

        WorkoutExerciseJpaEntity exercise = new WorkoutExerciseJpaEntity();
        exercise.setJefitLogId(100L);
        exercise.setExerciseName("Bench Press");
        exercise.setWorkoutJpaEntity(testWorkout);

        WorkoutSetJpaEntity set1 = new WorkoutSetJpaEntity();
        set1.setSetIndex(1);
        set1.setReps(3);
        set1.setWeight(60.0);
        set1.setExercise(exercise);

        WorkoutSetJpaEntity set2 = new WorkoutSetJpaEntity();
        set2.setSetIndex(2);
        set2.setReps(3);
        set2.setWeight(70.0);
        set2.setExercise(exercise);

        exercise.getSets().add(set1);
        exercise.getSets().add(set2);
        testWorkout.getExercises().add(exercise);

        workoutJpaRepository.saveAndFlush(testWorkout);

    }
    @Test
    void testWeeklySummary(){

        List<WorkoutSummaryWeeklyDto> listRs =
                workoutQueryUseCase.getAllWorkoutSummaryThisWeek(testUser.getId());


        assertThat(listRs).isNotEmpty();

        WorkoutSummaryWeeklyDto dto = listRs.get(0);

        assertThat(dto.getExerciseName()).isEqualTo("Bench Press");
        assertThat(dto.getTotalReps()).isEqualTo(6);
        assertThat(dto.getTotalWeight()).isEqualTo(130.0);


    }
}
