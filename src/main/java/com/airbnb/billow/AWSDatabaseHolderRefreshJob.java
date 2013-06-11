package com.airbnb.billow;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.SchedulerException;

public class AWSDatabaseHolderRefreshJob implements Job {
    public static final String DB_KEY = "db";
    public static final String NAME = "dbRefresh";

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            ((AWSDatabaseHolder) context.getScheduler().getContext().get(DB_KEY)).rebuild();
        } catch (SchedulerException e) {
            throw new JobExecutionException(e);
        }
    }
}
