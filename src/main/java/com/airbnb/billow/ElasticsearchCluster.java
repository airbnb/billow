package com.airbnb.billow;

import com.amazonaws.services.elasticsearch.model.ElasticsearchClusterConfig;
import com.amazonaws.services.elasticsearch.model.ElasticsearchDomainStatus;
import com.amazonaws.services.elasticsearch.model.Tag;
import lombok.Getter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ElasticsearchCluster {
  @Getter
  private final String domainName;
  @Getter
  private final Map<String, String> tags;
  @Getter
  private final String version;
  @Getter
  private final String instanceType;
  @Getter
  private final int instanceCount;
  @Getter
  private final Map<String, String> endpoints;
  @Getter
  private final boolean dedicatedMasterEnabled;
  @Getter
  private final boolean zoneAwarenessEnabled;
  @Getter
  private final String dedicatedMasterType;
  @Getter
  private final int dedicatedMasterCount;

  public ElasticsearchCluster(ElasticsearchDomainStatus domainStatus, List<Tag> tagList) {
    this.domainName = domainStatus.getDomainName();
    this.tags = new HashMap<>(tagList.size());
    for(Tag tag : tagList) {
      this.tags.put(tag.getKey(), tag.getValue());
    }
    this.version = domainStatus.getElasticsearchVersion();
    this.endpoints = domainStatus.getEndpoints();

    ElasticsearchClusterConfig esConfig = domainStatus.getElasticsearchClusterConfig();
    this.instanceType = esConfig.getInstanceType();
    this.instanceCount = esConfig.getInstanceCount();
    this.dedicatedMasterEnabled = esConfig.getDedicatedMasterEnabled();
    this.zoneAwarenessEnabled = esConfig.getZoneAwarenessEnabled();
    if (esConfig.getDedicatedMasterEnabled()) {
      this.dedicatedMasterCount = esConfig.getDedicatedMasterCount();
      this.dedicatedMasterType = esConfig.getDedicatedMasterType();
    } else {
      this.dedicatedMasterCount = 0;
      this.dedicatedMasterType = "";
    }
  }
}
