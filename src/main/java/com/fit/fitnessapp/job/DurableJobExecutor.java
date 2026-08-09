package com.fit.fitnessapp.job;

public interface DurableJobExecutor {

    boolean supports(String jobType);

    void execute(DurableJobDto job) throws Exception;
}
