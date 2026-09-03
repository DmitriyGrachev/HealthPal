package com.fit.fitnessapp.knowledge.adapter.in.job;

import com.fit.fitnessapp.job.DurableJobClaim;
import com.fit.fitnessapp.job.DurableJobDto;
import com.fit.fitnessapp.job.DurableJobExecutor;
import com.fit.fitnessapp.knowledge.application.service.MemoryProjectionRebuildService;
import org.springframework.stereotype.Component;

@Component
public class MemoryProjectionRebuildJobExecutor implements DurableJobExecutor {
    private final MemoryProjectionRebuildService rebuild;
    public MemoryProjectionRebuildJobExecutor(MemoryProjectionRebuildService rebuild) { this.rebuild = rebuild; }
    @Override public boolean supports(String jobType) { return MemoryProjectionRebuildService.JOB_TYPE.equals(jobType); }
    @Override public void execute(DurableJobDto job) { throw new IllegalStateException("REBUILD_EXECUTION_FENCE_REQUIRED"); }
    @Override public void executeClaim(DurableJobClaim claim) { rebuild.rebuild(claim); }
}
