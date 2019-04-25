package com.airbnb.billow;

import lombok.Getter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.amazonaws.services.elasticsearch.model.DomainInfo;
import com.amazonaws.services.elasticsearch.model.Tag;
import com.fasterxml.jackson.annotation.JsonFilter;

@JsonFilter(ElasticsearchCluster.DOMAIN_FILTER)
public class ElasticsearchCluster {
  public static final String DOMAIN_FILTER = "DomainFilter";

  @Getter
  private final String domainName;
  @Getter
  private final Map<String, String> tags;

  public ElasticsearchCluster(DomainInfo domainInfo, List<Tag> tagList) {
      this.domainName = domainInfo.getDomainName();
      this.tags = new HashMap<>(tagList.size());
      for(Tag tag : tagList) {
        this.tags.put(tag.getKey(), tag.getValue());
      }
  }
}
