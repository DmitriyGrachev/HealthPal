package com.fit.fitnessapp.workout.adapter.out.evidence;

import com.fit.fitnessapp.api.evidence.EvidenceSourceQuery;
import com.fit.fitnessapp.api.evidence.EvidenceSourceRequest;
import com.fit.fitnessapp.api.evidence.EvidenceSourceSlice;
import com.fit.fitnessapp.workout.application.port.in.WorkoutSourceStateQueryPort;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** Exposes only versioned Workout Day identities through the neutral evidence seam. */
@Component
public class WorkoutEvidenceSourceQuery implements EvidenceSourceQuery {

    private static final String SOURCE_TYPE = "WORKOUT_DAY";

    private final WorkoutSourceStateQueryPort states;

    public WorkoutEvidenceSourceQuery(WorkoutSourceStateQueryPort states) {
        this.states = states;
    }

    @Override
    public EvidenceSourceSlice query(EvidenceSourceRequest request) {
        List<EvidenceSourceSlice.EvidenceItem> items = new ArrayList<>();
        request.fromInclusive().datesUntil(request.toInclusive().plusDays(1)).forEach(date ->
                states.findCurrent(request.userId(), date)
                        .filter(state -> state.present())
                        .ifPresent(state -> items.add(new EvidenceSourceSlice.EvidenceItem(
                                state.sourceId(),
                                state.sourceVersion(),
                                state.contentHash(),
                                state.sourceDate(),
                                state.updatedAt()))));
        return new EvidenceSourceSlice(SOURCE_TYPE, List.copyOf(items));
    }
}
