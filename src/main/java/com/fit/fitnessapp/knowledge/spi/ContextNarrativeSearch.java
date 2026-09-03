package com.fit.fitnessapp.knowledge.spi;

import com.fit.fitnessapp.knowledge.context.UserContextRequest;
import java.util.List;

/** Optional projection lookup: identifiers only. Returned text can never replace canonical claim content. */
public interface ContextNarrativeSearch {
    Matches search(UserContextRequest request);

    record Candidate(Long claimId, long aggregateVersion, String contentHash) { }
    record Matches(boolean available, List<Candidate> candidates) {
        public Matches { candidates = List.copyOf(candidates); }
    }
}
