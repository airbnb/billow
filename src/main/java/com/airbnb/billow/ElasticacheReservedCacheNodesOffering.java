package com.airbnb.billow;

import lombok.Getter;

import com.amazonaws.services.elasticache.model.ReservedCacheNodesOffering;
import com.fasterxml.jackson.annotation.JsonFilter;

/**
 * Created by tong_wei on 3/15/16.
 */
@JsonFilter(ElasticacheReservedCacheNodesOffering.RESERVED_CACHE_NODE_OFFERING_FILTER)
public class ElasticacheReservedCacheNodesOffering {
    public static final String RESERVED_CACHE_NODE_OFFERING_FILTER = "ReservedCacheNodeOfferingFilter";

    @Getter
    private final String reservedCacheNodesOfferingId;
    @Getter
    private final String cacheNodeType;
    @Getter
    private final Integer duration;
    @Getter
    private final Double fixedPrice;
    @Getter
    private final Double usagePrice;
    @Getter
    private final String productDescription;
    @Getter
    private final String offeringType;
    @Getter
    private final String recurringCharges;

    public ElasticacheReservedCacheNodesOffering(ReservedCacheNodesOffering offering) {
        this.reservedCacheNodesOfferingId = offering.getReservedCacheNodesOfferingId();
        this.cacheNodeType = offering.getCacheNodeType();
        this.duration = offering.getDuration();
        this.fixedPrice = offering.getFixedPrice();
        this.usagePrice = offering.getUsagePrice();
        this.productDescription = offering.getProductDescription();
        this.offeringType = offering.getOfferingType();
        this.recurringCharges = offering.getRecurringCharges().toString();
    }
}
