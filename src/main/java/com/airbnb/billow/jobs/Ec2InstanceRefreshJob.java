package com.airbnb.billow.jobs;

import com.airbnb.billow.AWSDatabaseHolder;

public class Ec2InstanceRefreshJob extends BaseAWSDatabaseHolderRefreshJob {
    public static final String NAME = "ec2_instance_job";

    @Override
    void refresh(AWSDatabaseHolder dbHolder) {
        dbHolder.refreshEc2Instances();
    }
}
