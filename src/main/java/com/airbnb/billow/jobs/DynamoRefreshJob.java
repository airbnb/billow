package com.airbnb.billow.jobs;

import com.airbnb.billow.AWSDatabaseHolder;

public class DynamoRefreshJob extends BaseAWSDatabaseHolderRefreshJob {
    public static final String NAME = "dynamo_job";

    @Override
    void refresh(AWSDatabaseHolder dbHolder) {
        dbHolder.refreshDynamoTables();
    }
}
