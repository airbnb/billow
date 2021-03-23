package com.airbnb.billow.jobs;

import com.airbnb.billow.AWSDatabaseHolder;

public class Ec2SGRefreshJob extends BaseAWSDatabaseHolderRefreshJob {
    public static final String NAME = "ec2_sg_job";

    @Override
    void refresh(AWSDatabaseHolder dbHolder) {
        dbHolder.refreshEc2SGs();
    }
}
