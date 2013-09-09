package com.airbnb.billow;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.Region;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClient;
import com.typesafe.config.Config;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class AWSDatabaseHolder {
    private final List<AmazonEC2Client> ec2Clients;
    private final AmazonIdentityManagementClient iamClient;
    @Getter
    private AWSDatabase current;

    public AWSDatabaseHolder(Config config) {
        final BasicAWSCredentials awsCredentials = new BasicAWSCredentials(
                config.getString("accessKeyId"),
                config.getString("secretKeyId"));

        final AmazonEC2Client bootstrapClient = new AmazonEC2Client(awsCredentials);
        ec2Clients = new ArrayList<>();

        final List<Region> regions = bootstrapClient.describeRegions().getRegions();
        for (Region region : regions) {
            final String endpoint = region.getEndpoint();
            log.debug("Adding endpoint {} for region {}", endpoint, region);

            final AmazonEC2Client client = new AmazonEC2Client(awsCredentials);
            client.setEndpoint(endpoint);
            ec2Clients.add(client);
        }

        this.iamClient = new AmazonIdentityManagementClient(awsCredentials);

        rebuild();
    }

    public void rebuild() {
        current = new AWSDatabase(ec2Clients, iamClient);
    }
}
