package com.airbnb.billow.jobs;

import com.airbnb.billow.AWSDatabaseHolder;

public class IamRefreshJob extends BaseAWSDatabaseHolderRefreshJob {
    public static final String NAME = "iam_job";

    @Override
    void refresh(AWSDatabaseHolder dbHolder) {
        dbHolder.refreshIamUsers();
    }
}
