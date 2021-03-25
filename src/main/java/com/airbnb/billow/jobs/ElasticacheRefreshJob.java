package com.airbnb.billow.jobs;

import com.airbnb.billow.AWSDatabaseHolder;

public class ElasticacheRefreshJob extends BaseAWSDatabaseHolderRefreshJob {
    public static final String NAME = "elasticache_job";

    @Override
    void refresh(AWSDatabaseHolder dbHolder) {
        dbHolder.refreshElasticacheClusters();
    }
}
