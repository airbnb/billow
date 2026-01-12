package com.airbnb.billow;

import com.amazonaws.services.elasticache.model.Endpoint;
import com.amazonaws.services.elasticache.model.NodeGroupMember;
import lombok.Getter;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.amazonaws.services.elasticache.model.CacheCluster;
import com.amazonaws.services.elasticache.model.Tag;
import com.fasterxml.jackson.annotation.JsonFilter;

/**
 * Created by tong_wei on 3/15/16.
 */
@JsonFilter(ElasticacheCluster.CACHE_CLUSTER_FILTER)
public class ElasticacheCluster {
    public static final String CACHE_CLUSTER_FILTER = "CacheClusterFilter";

    @Getter
    private final String cacheClusterId;
    @Getter String configurationEndpoint;
    private final String clientDownloadLandingPage;
    @Getter
    private final String cacheNodeType;
    @Getter
    private final String engine;
    @Getter
    private final String engineVersion;
    @Getter
    private final String cacheClusterStatus;
    @Getter
    private final Integer numCacheNodes;
    @Getter
    private final String preferredAvailabilityZone;
    @Getter
    private final Date cacheClusterCreateTime;
    @Getter
    private final String preferredMaintenanceWindow;
    @Getter
    private final String pendingModifiedValues;
    @Getter
    private final String notificationConfiguration;
    @Getter
    private final String cacheSecurityGroups;
    @Getter
    private final String cacheParameterGroup;
    @Getter
    private final String cacheSubnetGroupName;
    @Getter
    private final String cacheNodes;
    @Getter
    private final Boolean autoMinorVersionUpgrade;
    @Getter
    private final String securityGroups;
    @Getter
    private final String replicationGroupId;
    @Getter
    private final Integer snapshotRetentionLimit;
    @Getter
    private final String snapshotWindow;
    @Getter
    private final Endpoint endpoint;
    @Getter
    private final String currentRole;
    @Getter
    private final Map<String, String> tags;


    public ElasticacheCluster(CacheCluster cacheCluster, NodeGroupMember nodeGroupMember, List<Tag> tagList) {
        this.cacheClusterId = cacheCluster.getCacheClusterId();
        if (cacheCluster.getConfigurationEndpoint() != null) {
            this.configurationEndpoint = cacheCluster.getConfigurationEndpoint().toString();
        }
        this.clientDownloadLandingPage = cacheCluster.getClientDownloadLandingPage();
        this.cacheNodeType = cacheCluster.getCacheNodeType();
        this.engine = cacheCluster.getEngine();
        this.engineVersion = cacheCluster.getEngineVersion();
        this.cacheClusterStatus = cacheCluster.getCacheClusterStatus();
        this.numCacheNodes = cacheCluster.getNumCacheNodes();
        this.preferredAvailabilityZone = cacheCluster.getPreferredAvailabilityZone();
        this.cacheClusterCreateTime = cacheCluster.getCacheClusterCreateTime();
        this.preferredMaintenanceWindow = cacheCluster.getPreferredMaintenanceWindow();
        this.pendingModifiedValues = cacheCluster.getPendingModifiedValues().toString();
        if (cacheCluster.getNotificationConfiguration() != null) {
            this.notificationConfiguration = cacheCluster.getNotificationConfiguration().toString();
        } else {
            this.notificationConfiguration = "empty";
        }
        this.cacheSecurityGroups = cacheCluster.getSecurityGroups().toString();
        this.cacheParameterGroup = cacheCluster.getCacheParameterGroup().toString();
        this.cacheSubnetGroupName = cacheCluster.getCacheSubnetGroupName();
        this.cacheNodes = cacheCluster.getCacheNodes().toString();
        this.autoMinorVersionUpgrade = cacheCluster.getAutoMinorVersionUpgrade();
        this.securityGroups = cacheCluster.getSecurityGroups().toString();
        this.replicationGroupId = cacheCluster.getReplicationGroupId();
        this.snapshotRetentionLimit = cacheCluster.getSnapshotRetentionLimit();
        this.snapshotWindow = cacheCluster.getSnapshotWindow();
        if (nodeGroupMember != null) {
            this.endpoint = nodeGroupMember.getReadEndpoint();
            this.currentRole = nodeGroupMember.getCurrentRole();
        } else {
            this.endpoint = cacheCluster.getConfigurationEndpoint();
            this.currentRole = null;
        }
        this.tags = new HashMap<>(tagList.size());
        for(Tag tag : tagList) {
            this.tags.put(tag.getKey(), tag.getValue());
        }
    }
}
