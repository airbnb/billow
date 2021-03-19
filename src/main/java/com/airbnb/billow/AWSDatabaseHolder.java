package com.airbnb.billow;

import com.codahale.metrics.MetricRegistry;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.retry.RetryPolicy;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.Region;
import com.amazonaws.services.elasticache.AmazonElastiCacheClient;
import com.amazonaws.services.elasticsearch.AWSElasticsearchClient;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagement;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClientBuilder;
import com.amazonaws.services.rds.AmazonRDSClient;
import com.amazonaws.services.sqs.AmazonSQSClient;
import com.codahale.metrics.health.HealthCheck;
import com.google.common.collect.Maps;
import com.typesafe.config.Config;

@Slf4j
public class AWSDatabaseHolder {
    private final Map<String, AmazonEC2Client> ec2Clients;
    private final Map<String, AmazonRDSClient> rdsClients;
    private final Map<String, AmazonDynamoDBClient> dynamoDBClients;
    private final Map<String, AmazonSQSClient> sqsClients;
    private final Map<String, AmazonElastiCacheClient> elasticacheClients;
    private final Map<String, AWSElasticsearchClient> elasticsearchClients;
    private final AmazonIdentityManagement iamClient;
    @Getter
    private AWSDatabase current;
    private final long maxAgeInMs;
    private final String awsAccountNumber;
    private final String awsARNPartition;

    public AWSDatabaseHolder(Config config) {
        maxAgeInMs = config.getDuration("maxAge", TimeUnit.MILLISECONDS);

        final DefaultAWSCredentialsProviderChain awsCredentialsProviderChain = new DefaultAWSCredentialsProviderChain();

        final ClientConfiguration clientConfig = new ClientConfiguration();
        clientConfig.setRetryPolicy(new RetryPolicy(null, null, config.getInt("maxErrorRetry"), true));
        clientConfig.setSocketTimeout(config.getInt("socketTimeout") * 1000);

        final AmazonEC2 bootstrapEC2Client = AmazonEC2ClientBuilder.standard().withCredentials(awsCredentialsProviderChain).build();

        ec2Clients = Maps.newHashMap();
        rdsClients = Maps.newHashMap();
        sqsClients = Maps.newHashMap();
        dynamoDBClients = Maps.newHashMap();
        elasticacheClients = Maps.newHashMap();
        elasticsearchClients = Maps.newHashMap();

        final List<Region> ec2Regions = bootstrapEC2Client.describeRegions().getRegions();
        for (Region region : ec2Regions) {
            final String regionName = region.getRegionName();
            final String endpoint = region.getEndpoint();
            log.debug("Adding ec2 region {}", region);

            if (config.getBoolean("ec2Enabled")) {
                final AmazonEC2Client ec2Client = new AmazonEC2Client(awsCredentialsProviderChain, clientConfig);
                ec2Client.setEndpoint(endpoint);
                ec2Clients.put(regionName, ec2Client);
            }

            if (config.getBoolean("rdsEnabled")) {
                final AmazonRDSClient rdsClient = new AmazonRDSClient(awsCredentialsProviderChain, clientConfig);
                rdsClient.setEndpoint(endpoint.replaceFirst("ec2\\.", "rds."));
                rdsClients.put(regionName, rdsClient);
            }

            if (config.getBoolean("dynamodbEnabled")) {
                final AmazonDynamoDBClient dynamoDBClient =
                    new AmazonDynamoDBClient(awsCredentialsProviderChain, clientConfig);
                dynamoDBClient.setEndpoint(endpoint.replaceFirst("ec2\\.", "dynamodb."));
                dynamoDBClients.put(regionName, dynamoDBClient);
            }

            if (config.getBoolean("sqsEnabled")) {
                final AmazonSQSClient sqsClient = new AmazonSQSClient(awsCredentialsProviderChain, clientConfig);
                sqsClient.setEndpoint(endpoint.replaceFirst("ec2\\.", "sqs."));
                sqsClients.put(regionName, sqsClient);
            }

            if (config.getBoolean("elasticacheEnabled")) {
                final AmazonElastiCacheClient elastiCacheClient = new AmazonElastiCacheClient
                    (awsCredentialsProviderChain, clientConfig);
                elastiCacheClient.setEndpoint(endpoint.replaceFirst("ec2\\.", "elasticache."));
                elasticacheClients.put(regionName, elastiCacheClient);
            }

            if (config.getBoolean("elasticsearchEnabled")) {
                final AWSElasticsearchClient elasticsearchClient = new AWSElasticsearchClient
                    (awsCredentialsProviderChain, clientConfig);
                elasticsearchClient.setEndpoint(endpoint.replaceFirst("ec2\\.", "es."));
                elasticsearchClients.put(regionName, elasticsearchClient);
            }
        }

        this.iamClient = AmazonIdentityManagementClientBuilder.standard()
            .withCredentials(awsCredentialsProviderChain)
            .withClientConfiguration(clientConfig)
            .build();

        if (config.hasPath("accountNumber")) {
            this.awsAccountNumber = config.getString("accountNumber");
        } else {
            this.awsAccountNumber = null;
        }

        if (config.hasPath("arnPartition")) {
            this.awsARNPartition = config.getString("arnPartition");
        } else {
            this.awsARNPartition = "aws";
        }

        rebuild();
    }

    public void rebuild() {
        current = new AWSDatabase(
            ec2Clients,
            rdsClients,
            dynamoDBClients,
            sqsClients,
            elasticacheClients,
            elasticsearchClients,
            iamClient,
            awsAccountNumber,
            awsARNPartition);
    }

    public HealthCheck.Result healthy() {
        final long ageInMs = current.getAgeInMs();
        if (ageInMs < maxAgeInMs)
            return HealthCheck.Result.healthy();
        else
            return HealthCheck.Result.unhealthy("DB too old: " + ageInMs + " ms");
    }

    public long getCacheTimeInMs() {
        return maxAgeInMs - current.getAgeInMs();
    }
}
