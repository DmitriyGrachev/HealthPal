package com.fit.fitnessapp.nutrition.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fit.fitnessapp.job.DurableJobUseCase;
import com.fit.fitnessapp.nutrition.application.port.in.SyncNutritionUseCase;
import com.fit.fitnessapp.nutrition.application.port.out.NutritionCommandPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class NutritionSyncSchedulerTest {

    @Mock
    private SyncNutritionUseCase syncUseCase;

    @Mock
    private NutritionCommandPort nutritionCommandPort;

    @Mock
    private DurableJobUseCase durableJobUseCase;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        lenient().when(durableJobUseCase.createJob(anyString(), any(), anyString(), anyString()))
                .thenReturn(100L);
        lenient().when(durableJobUseCase.startJob(100L)).thenReturn(true);
    }

    @Test
    void syncsTodayForEveryConnectedUserUsingConfiguredClock() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-06T20:30:00Z"), ZoneOffset.UTC);
        LocalDate expectedDate = LocalDate.of(2026, 7, 6);
        when(nutritionCommandPort.getAllConnectedUserIds()).thenReturn(List.of(41L, 42L));

        new NutritionSyncScheduler(syncUseCase, nutritionCommandPort, clock, durableJobUseCase, objectMapper).syncAllUsersToday();

        InOrder inOrder = inOrder(syncUseCase);
        inOrder.verify(syncUseCase).syncDay(41L, expectedDate);
        inOrder.verify(syncUseCase).syncDay(42L, expectedDate);
    }

    @Test
    void syncsRecentWindowForEveryConnectedUser() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-06T20:30:00Z"), ZoneOffset.UTC);
        when(nutritionCommandPort.getAllConnectedUserIds()).thenReturn(List.of(42L));

        new NutritionSyncScheduler(syncUseCase, nutritionCommandPort, clock, durableJobUseCase, objectMapper, 2).syncAllUsersRecentWindow();

        InOrder inOrder = inOrder(syncUseCase);
        inOrder.verify(syncUseCase).syncDay(42L, LocalDate.of(2026, 7, 6));
        inOrder.verify(syncUseCase).syncDay(42L, LocalDate.of(2026, 7, 5));
    }

    @Test
    void keepsSyncingRemainingUsersWhenOneUserFails(CapturedOutput output) {
        Clock clock = Clock.fixed(Instant.parse("2026-07-06T20:30:00Z"), ZoneOffset.UTC);
        LocalDate expectedDate = LocalDate.of(2026, 7, 6);
        when(nutritionCommandPort.getAllConnectedUserIds()).thenReturn(List.of(41L, 42L, 43L));
        doAnswer(invocation -> {
                    Long userId = invocation.getArgument(0);
                    if (userId.equals(42L)) {
                        throw new IllegalStateException("fatsecret unavailable");
                    }
                    return null;
                })
                .when(syncUseCase)
                .syncDay(any(), any());

        assertThatCode(() -> new NutritionSyncScheduler(syncUseCase, nutritionCommandPort, clock, durableJobUseCase, objectMapper).syncAllUsersToday())
                .doesNotThrowAnyException();

        InOrder inOrder = inOrder(syncUseCase);
        inOrder.verify(syncUseCase).syncDay(41L, expectedDate);
        inOrder.verify(syncUseCase).syncDay(42L, expectedDate);
        inOrder.verify(syncUseCase).syncDay(43L, expectedDate);
        assertThat(output)
                .contains("success=2")
                .contains("failed=1")
                .doesNotContain("fatsecret unavailable");
    }

    @Test
    void doesNotExecuteDuplicateJobThatAnotherWorkerAlreadyClaimed() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-06T20:30:00Z"), ZoneOffset.UTC);
        when(nutritionCommandPort.getAllConnectedUserIds()).thenReturn(List.of(42L));
        org.mockito.Mockito.doReturn(100L).when(durableJobUseCase).createJob(
                eq(NutritionSyncJobExecutor.JOB_TYPE),
                eq(42L),
                anyString(),
                eq("nutrition-sync:v1:42:2026-07-06:1"));
        org.mockito.Mockito.doReturn(false).when(durableJobUseCase).startJob(100L);

        new NutritionSyncScheduler(syncUseCase, nutritionCommandPort, clock, durableJobUseCase, objectMapper)
                .syncAllUsersToday();

        verify(syncUseCase, never()).syncDay(any(), any());
    }

    @Test
    void doesNothingWhenNoUsersHaveFatSecretConnected() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-06T20:30:00Z"), ZoneOffset.UTC);
        when(nutritionCommandPort.getAllConnectedUserIds()).thenReturn(List.of());

        new NutritionSyncScheduler(syncUseCase, nutritionCommandPort, clock, durableJobUseCase, objectMapper).syncAllUsersToday();

        verifyNoInteractions(syncUseCase);
    }

    @Test
    void skipsOverlappingRunInTheSameInstance() throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2026-07-06T20:30:00Z"), ZoneOffset.UTC);
        when(nutritionCommandPort.getAllConnectedUserIds()).thenReturn(List.of(42L));
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
            entered.countDown();
            assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
            return null;
        }).when(syncUseCase).syncDay(any(), any());

        NutritionSyncScheduler scheduler = new NutritionSyncScheduler(
                syncUseCase, nutritionCommandPort, clock, durableJobUseCase, objectMapper);
        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            var firstRun = executor.submit(scheduler::syncAllUsersToday);
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();

            scheduler.syncAllUsersToday();
            release.countDown();
            firstRun.get(5, TimeUnit.SECONDS);
        }

        verify(syncUseCase).syncDay(42L, LocalDate.of(2026, 7, 6));
    }

    @Test
    void springCanCreateSchedulerWithProductionConstructor() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(SyncNutritionUseCase.class, () -> syncUseCase);
            context.registerBean(NutritionCommandPort.class, () -> nutritionCommandPort);
            context.registerBean(Clock.class, () -> Clock.systemUTC());
            context.registerBean(DurableJobUseCase.class, () -> durableJobUseCase);
            context.registerBean(ObjectMapper.class, () -> objectMapper);
            context.register(NutritionSyncScheduler.class);

            context.refresh();

            assertThat(context.getBean(NutritionSyncScheduler.class)).isNotNull();
        }
    }
}
