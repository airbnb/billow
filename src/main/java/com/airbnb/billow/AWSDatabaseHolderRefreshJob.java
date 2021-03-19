package com.airbnb.billow;

import com.codahale.metrics.Counter;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.SchedulerException;

public class AWSDatabaseHolderRefreshJob implements Job {
    public static final String DB_KEY              = "db";
    public static final String FAILURE_COUNTER_KEY = "failure_counter";
    public static final String SUCCESS_COUNTER_KEY = "success_counter";
    public static final String NAME                = "dbRefresh";

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            ((AWSDatabaseHolder) context.getScheduler().getContext().get(DB_KEY)).rebuild();
            increment(SUCCESS_COUNTER_KEY, context);
        } catch (SchedulerException e) {
            increment(FAILURE_COUNTER_KEY, context);
            throw new JobExecutionException(e);
        }
    }

    private void increment(String counterName, JobExecutionContext context) {
        try {
            ((Counter) context.getScheduler().getContext().get(counterName)).inc();
        } catch (SchedulerException e) {
            // do nothing, don't throw an error for metrics
        }
    }
}
