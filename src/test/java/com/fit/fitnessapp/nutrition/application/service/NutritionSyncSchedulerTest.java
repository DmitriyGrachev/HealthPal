package com.fit.fitnessapp.nutrition.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fit.fitnessapp.job.DurableJobClaim;
import com.fit.fitnessapp.job.DurableJobDto;
import com.fit.fitnessapp.job.application.service.DurableJobLeaseHeartbeat;
import com.fit.fitnessapp.job.DurableJobUseCase;
import com.fit.fitnessapp.job.JobStatus;
import com.fit.fitnessapp.nutrition.application.port.in.SyncNutritionUseCase;
import com.fit.fitnessapp.nutrition.application.port.out.NutritionCommandPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class NutritionSyncSchedulerTest {

    @Mock private SyncNutritionUseCase syncUseCase;
    @Mock private NutritionCommandPort nutritionCommandPort;
    @Mock private DurableJobUseCase durableJobUseCase;
    @Mock private DurableJobLeaseHeartbeat leaseHeartbeat;
    private final DurableJobLeaseHeartbeat.Registration registration = mock(DurableJobLeaseHeartbeat.Registration.class);

    @Test
    void claimsAndExecutesTheRefreshedPayloadUsingTheSameClaimForCompletion(CapturedOutput output) {
        Clock clock = Clock.fixed(Instant.parse("2026-07-06T20:30:00Z"), ZoneOffset.UTC);
        when(nutritionCommandPort.getAllConnectedUserIds()).thenReturn(List.of(42L));
        when(durableJobUseCase.createJob(anyString(), eq(42L), anyString(), anyString())).thenReturn(100L);
        DurableJobClaim claim = claim(100L, 99L, "{\"dates\":[\"2026-07-04\"]}");
        when(durableJobUseCase.claimJob(eq(100L), anyString(), any())).thenReturn(Optional.of(claim));
        when(durableJobUseCase.completeJob(claim)).thenReturn(true);
        NutritionSyncJobExecutor executor = new NutritionSyncJobExecutor(syncUseCase, mapper());
        when(leaseHeartbeat.track(eq(claim), any())).thenReturn(registration);

        new NutritionSyncScheduler(syncUseCase, nutritionCommandPort, clock, durableJobUseCase,
                mapper(), executor, leaseHeartbeat, 1).syncAllUsersToday();

        verify(syncUseCase).syncDay(99L, LocalDate.of(2026, 7, 4));
        verify(syncUseCase, never()).syncDay(42L, LocalDate.of(2026, 7, 6));
        verify(durableJobUseCase).completeJob(claim);
        assertThat(output).contains("success=1 failed=0");
        var order = inOrder(leaseHeartbeat, syncUseCase, durableJobUseCase, registration);
        order.verify(leaseHeartbeat).track(eq(claim), any());
        order.verify(syncUseCase).syncDay(99L, LocalDate.of(2026, 7, 4));
        order.verify(durableJobUseCase).completeJob(claim);
        order.verify(registration).close();
    }

    @Test
    void doesNotExecuteWhenAnotherWorkerOwnsTheNewJob() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-06T20:30:00Z"), ZoneOffset.UTC);
        when(nutritionCommandPort.getAllConnectedUserIds()).thenReturn(List.of(42L));
        when(durableJobUseCase.createJob(anyString(), eq(42L), anyString(), anyString())).thenReturn(100L);
        when(durableJobUseCase.claimJob(eq(100L), anyString(), any())).thenReturn(Optional.empty());

        new NutritionSyncScheduler(syncUseCase, nutritionCommandPort, clock, durableJobUseCase,
                mapper()).syncAllUsersToday();

        verify(syncUseCase, never()).syncDay(any(), any());
    }

    @Test
    void continuesWithRemainingUsersAfterOneClaimedExecutionFails() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-06T20:30:00Z"), ZoneOffset.UTC);
        when(nutritionCommandPort.getAllConnectedUserIds()).thenReturn(List.of(41L, 42L));
        when(durableJobUseCase.createJob(anyString(), any(), anyString(), anyString())).thenReturn(100L, 101L);
        when(durableJobUseCase.claimJob(eq(100L), anyString(), any())).thenReturn(Optional.of(claim(100L, 41L, "{\"dates\":[\"2026-07-06\"]}")));
        when(durableJobUseCase.claimJob(eq(101L), anyString(), any())).thenReturn(Optional.of(claim(101L, 42L, "{\"dates\":[\"2026-07-06\"]}")));
        doAnswer(invocation -> {
            if (invocation.getArgument(0, Long.class).equals(41L)) {
                throw new IllegalStateException("provider response");
            }
            return null;
        }).when(syncUseCase).syncDay(any(), any());
        when(leaseHeartbeat.track(any(), any())).thenReturn(registration);

        new NutritionSyncScheduler(syncUseCase, nutritionCommandPort, clock, durableJobUseCase,
                mapper(), new NutritionSyncJobExecutor(syncUseCase, mapper()), leaseHeartbeat, 1)
                .syncAllUsersToday();

        verify(syncUseCase).syncDay(41L, LocalDate.of(2026, 7, 6));
        verify(syncUseCase).syncDay(42L, LocalDate.of(2026, 7, 6));
        verify(durableJobUseCase).failJob(any(DurableJobClaim.class), any());
        var order = inOrder(leaseHeartbeat, syncUseCase, durableJobUseCase, registration);
        order.verify(leaseHeartbeat).track(any(DurableJobClaim.class), any());
        order.verify(syncUseCase).syncDay(41L, LocalDate.of(2026, 7, 6));
        order.verify(durableJobUseCase).failJob(any(DurableJobClaim.class), any());
        order.verify(registration).close();
    }

    @Test
    void overlappingRunIsSkipped() throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2026-07-06T20:30:00Z"), ZoneOffset.UTC);
        when(nutritionCommandPort.getAllConnectedUserIds()).thenReturn(List.of(42L));
        when(durableJobUseCase.createJob(anyString(), any(), anyString(), anyString())).thenReturn(100L);
        DurableJobClaim claim = claim("{\"dates\":[\"2026-07-06\"]}");
        when(durableJobUseCase.claimJob(eq(100L), anyString(), any())).thenReturn(Optional.of(claim));
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
            entered.countDown();
            assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
            return null;
        }).when(syncUseCase).syncDay(any(), any());
        NutritionSyncScheduler scheduler = new NutritionSyncScheduler(syncUseCase, nutritionCommandPort,
                clock, durableJobUseCase, mapper());
        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            var first = executor.submit(scheduler::syncAllUsersToday);
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            scheduler.syncAllUsersToday();
            release.countDown();
            first.get(5, TimeUnit.SECONDS);
        }
        verify(syncUseCase).syncDay(42L, LocalDate.of(2026, 7, 6));
    }

    private ObjectMapper mapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }

    private DurableJobClaim claim(String payload) {
        return claim(100L, 42L, payload);
    }

    private DurableJobClaim claim(long id, long userId, String payload) {
        Instant now = Instant.parse("2026-07-06T20:30:00Z");
        DurableJobDto job = new DurableJobDto(id, NutritionSyncJobExecutor.JOB_TYPE, userId,
                JobStatus.RUNNING, 1, 3, null, null, payload, "nutrition-key", now, now);
        return new DurableJobClaim(job, "nutrition-scheduler", 1, now.plusSeconds(900));
    }
}
