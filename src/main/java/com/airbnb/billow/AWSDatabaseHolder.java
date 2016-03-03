package com.airbnb.billow;

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
import com.amazonaws.services.ec2.model.Region;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClient;
import com.amazonaws.services.rds.AmazonRDSClient;
import com.amazonaws.services.sqs.AmazonSQSClient;
import com.codahale.metrics.health.HealthCheck;
import com.google.common.collect.Maps;
import com.typesafe.config.Config;

@Slf4j
public class AWSDatabaseHolder {
    private final Map<String, AmazonEC2Client> ec2Clients;
    private final Map<String, AmazonRDSClient> rdsClients;
    private final Map<String, AmazonSQSClient> sqsClients;
    private final Map<String, AmazonDynamoDBClient> dynamoDBClients;
    private final AmazonIdentityManagementClient iamClient;
    @Getter
    private AWSDatabase current;
    private final long maxAgeInMs;
    private final String awsAccountNumber;

    public AWSDatabaseHolder(Config config) {
        maxAgeInMs = config.getDuration("maxAge", TimeUnit.MILLISECONDS);

        final DefaultAWSCredentialsProviderChain awsCredentialsProviderChain = new DefaultAWSCredentialsProviderChain();

        final ClientConfiguration clientConfig = new ClientConfiguration();
        clientConfig.setRetryPolicy(new RetryPolicy(null, null, config.getInt("maxErrorRetry"), true));

        final AmazonEC2Client bootstrapEC2Client = new AmazonEC2Client(awsCredentialsProviderChain);
        ec2Clients = Maps.newHashMap();
        rdsClients = Maps.newHashMap();
        dynamoDBClients = Maps.newHashMap();
        sqsClients = Maps.newHashMap();

        final List<Region> ec2Regions = bootstrapEC2Client.describeRegions().getRegions();
        for (Region region : ec2Regions) {
            final String regionName = region.getRegionName();
            final String endpoint = region.getEndpoint();
            log.debug("Adding ec2 region {}", region);

            final AmazonEC2Client ec2Client = new AmazonEC2Client(awsCredentialsProviderChain, clientConfig);
            ec2Client.setEndpoint(endpoint);
            ec2Clients.put(regionName, ec2Client);

            final AmazonDynamoDBClient dynamoDBClient =
                new AmazonDynamoDBClient(awsCredentialsProviderChain, clientConfig);
            dynamoDBClient.setEndpoint(endpoint.replaceFirst("ec2\\.", "dynamodb."));
            dynamoDBClients.put(regionName, dynamoDBClient);

            final AmazonRDSClient rdsClient = new AmazonRDSClient(awsCredentialsProviderChain, clientConfig);
            rdsClient.setEndpoint(endpoint.replaceFirst("ec2\\.", "rds."));
            rdsClients.put(regionName, rdsClient);
        }

        this.iamClient = new AmazonIdentityManagementClient(awsCredentialsProviderChain, clientConfig);

        if (config.hasPath("accountNumber")) {
            this.awsAccountNumber = config.getString("accountNumber");
        } else {
            this.awsAccountNumber = null;
        }

        rebuild();
    }

    public void rebuild() {
        current = new AWSDatabase(ec2Clients, rdsClients, dynamoDBClients, iamClient, awsAccountNumber);
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
