package com.airbnb.billow.jobs;

import com.airbnb.billow.AWSDatabaseHolder;

public class ElasticsearchRefreshJob extends BaseAWSDatabaseHolderRefreshJob {
    public static final String NAME = "elasticsearch_job";

    @Override
    void refresh(AWSDatabaseHolder dbHolder) {
        dbHolder.refreshElasticsearchClusters();
    }
}
