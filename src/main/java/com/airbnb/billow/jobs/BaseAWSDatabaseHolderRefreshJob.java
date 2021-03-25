package com.airbnb.billow.jobs;

import com.airbnb.billow.AWSDatabaseHolder;
import com.codahale.metrics.Counter;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.SchedulerException;

@Slf4j
public abstract class BaseAWSDatabaseHolderRefreshJob implements Job {
    public static final String DB_KEY              = "db";
    public static final String FAILURE_COUNTER_KEY = "failure_counter";
    public static final String START_COUNTER_KEY   = "start_counter";
    public static final String SUCCESS_COUNTER_KEY = "success_counter";

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            increment(START_COUNTER_KEY, context);
            ((AWSDatabaseHolder) context.getScheduler().getContext().get(DB_KEY)).rebuild();
            log.info("[job success] {} completed", this.getClass().toString());
            increment(SUCCESS_COUNTER_KEY, context);
        } catch (SchedulerException e) {
            log.error("[job failure] {} completed", this.getClass().toString());
            increment(FAILURE_COUNTER_KEY, context);
            throw new JobExecutionException(e);
        }
    }

    abstract void refresh(AWSDatabaseHolder dbHolder);

    private void increment(String counterName, JobExecutionContext context) {
        try {
            ((Counter) context.getScheduler().getContext().get(counterName)).inc();
        } catch (SchedulerException e) {
            // do nothing, don't throw an error for metrics
        }
    }
}
