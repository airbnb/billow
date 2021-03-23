package com.airbnb.billow.jobs;

import com.airbnb.billow.AWSDatabaseHolder;

public class SqsRefreshJob extends BaseAWSDatabaseHolderRefreshJob {
    public static final String NAME = "sqs_job";

    @Override
    void refresh(AWSDatabaseHolder dbHolder) {
        dbHolder.refreshSqsQueues();
    }
}
