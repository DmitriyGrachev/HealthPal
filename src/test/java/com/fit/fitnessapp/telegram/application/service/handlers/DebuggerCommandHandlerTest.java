package com.fit.fitnessapp.telegram.application.service.handlers;

import com.fit.fitnessapp.auth.UserTimeApi;
import com.fit.fitnessapp.experiment.application.port.in.DebuggerWorkflowUseCase;
import com.fit.fitnessapp.telegram.application.port.in.ConversationStateUseCase;
import com.fit.fitnessapp.telegram.application.port.in.InboundCommand;
import com.fit.fitnessapp.telegram.application.service.TelegramBotService;
import com.fit.fitnessapp.telegram.application.service.TelegramMessages;
import com.fit.fitnessapp.telegram.domain.ConversationState;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DebuggerCommandHandlerTest {

    @Test
    void dispatchesAllDebuggerCommandsWithTypedStateAndStableUpdateKey() {
        TelegramBotService bot = mock(TelegramBotService.class);
        ConversationStateUseCase states = mock(ConversationStateUseCase.class);
        UserTimeApi userTime = mock(UserTimeApi.class);
        DebuggerWorkflowUseCase workflow = mock(DebuggerWorkflowUseCase.class);
        DebuggerCommandHandler handler = new DebuggerCommandHandler(bot, states, userTime, workflow);
        when(userTime.currentDate(42L)).thenReturn(LocalDate.of(2026, 8, 28));
        when(userTime.getTimeZone(42L)).thenReturn("Europe/Chisinau");
        when(states.updateState(anyLong(), anyLong(), any(), any())).thenReturn(true);
        when(workflow.createGoal(anyLong(), anyString(), anyString())).thenReturn(
                new DebuggerWorkflowUseCase.GoalResult(10L, 20L, "DRAFT", 0));
        when(workflow.createExperiment(anyLong(), any(), anyString())).thenReturn(
                new DebuggerWorkflowUseCase.ExperimentResult(30L, "DRAFT", 0));
        when(workflow.recordCheckIn(anyLong(), any(), anyString())).thenReturn(
                new DebuggerWorkflowUseCase.EvidenceResult(40L, true, "CHECK_IN"));
        when(workflow.recordOutcome(anyLong(), any(), anyString())).thenReturn(
                new DebuggerWorkflowUseCase.EvidenceResult(50L, true, "OUTCOME"));
        when(workflow.evaluate(anyLong(), anyLong(), anyLong(), anyString())).thenReturn(
                new DebuggerWorkflowUseCase.EvaluationResult(60L, true, "KEEP", "SUFFICIENT"));
        when(states.getData(456L)).thenReturn(
                Map.of("investigationId", 10L, "goalId", 20L),
                Map.of("experimentId", 30L, "experimentVersion", 0L));

        handler.handle(command(101, InboundCommand.Type.GOAL, "Improve recovery"));
        handler.handle(command(102, InboundCommand.Type.EXPERIMENT,
                "Recovery improves | Sleep on schedule | Lights out by 23:00 | recovery | 3 | 7 | INCREASE | 1 | Stop on illness"));
        handler.handle(command(103, InboundCommand.Type.CHECKIN,
                "YES | 1 | 8 | 7 | 8 | on plan"));
        handler.handle(command(104, InboundCommand.Type.OUTCOME,
                "recovery | 5 | 7 | points | 3 | 7 | completed"));
        handler.handle(command(105, InboundCommand.Type.EVALUATE, ""));

        verify(workflow).createGoal(42L, "Improve recovery", "telegram:101");
        ArgumentCaptor<DebuggerWorkflowUseCase.ExperimentDraft> draft =
                ArgumentCaptor.forClass(DebuggerWorkflowUseCase.ExperimentDraft.class);
        verify(workflow).createExperiment(org.mockito.ArgumentMatchers.eq(42L),
                draft.capture(), org.mockito.ArgumentMatchers.eq("telegram:102"));
        assertThat(draft.getValue().investigationId()).isEqualTo(10L);
        assertThat(draft.getValue().goalId()).isEqualTo(20L);
        assertThat(draft.getValue().baselineStartDate()).isEqualTo(LocalDate.of(2026, 8, 26));
        verify(workflow).evaluate(42L, 30L, 0L, "telegram:105");
        verify(bot, atLeast(5)).enqueueOwnedMessage(
                org.mockito.ArgumentMatchers.eq(42L),
                org.mockito.ArgumentMatchers.eq(456L), anyString());
    }

    @Test
    void invalidOrStateLessDebuggerCommandsNeverReachProductUseCases() {
        TelegramBotService bot = mock(TelegramBotService.class);
        ConversationStateUseCase states = mock(ConversationStateUseCase.class);
        UserTimeApi userTime = mock(UserTimeApi.class);
        DebuggerWorkflowUseCase workflow = mock(DebuggerWorkflowUseCase.class);
        DebuggerCommandHandler handler = new DebuggerCommandHandler(bot, states, userTime, workflow);
        when(states.getData(456L)).thenReturn(Map.of());

        handler.handle(command(201, InboundCommand.Type.GOAL, ""));
        handler.handle(command(202, InboundCommand.Type.EXPERIMENT, "broken"));
        handler.handle(command(203, InboundCommand.Type.CHECKIN, "YES"));
        handler.handle(command(204, InboundCommand.Type.OUTCOME, "broken"));
        handler.handle(command(205, InboundCommand.Type.EVALUATE, ""));

        verify(workflow, never()).createGoal(anyLong(), anyString(), anyString());
        verify(workflow, never()).createExperiment(anyLong(), any(), anyString());
        verify(workflow, never()).recordCheckIn(anyLong(), any(), anyString());
        verify(workflow, never()).recordOutcome(anyLong(), any(), anyString());
        verify(workflow, never()).evaluate(anyLong(), anyLong(), anyLong(), anyString());
        verify(bot, atLeast(5)).enqueueOwnedMessage(42L, 456L, TelegramMessages.COMMAND_INVALID);
    }

    private static InboundCommand command(int updateId, InboundCommand.Type type, String payload) {
        return new InboundCommand(updateId, 456L, 123L, 42L, type, payload);
    }
}
