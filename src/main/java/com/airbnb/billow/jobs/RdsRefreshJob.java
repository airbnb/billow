package com.airbnb.billow.jobs;

import com.airbnb.billow.AWSDatabaseHolder;

public class RdsRefreshJob extends BaseAWSDatabaseHolderRefreshJob{
    public static final String NAME = "rds_job";

    @Override
    void refresh(AWSDatabaseHolder dbHolder) {
        dbHolder.refreshRdsInstances();
    }
}
