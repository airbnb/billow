package com.airbnb.billow;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClient;
import com.typesafe.config.Config;
import lombok.Getter;

public class AWSDatabaseHolder {
    private final AmazonEC2Client ec2Client;
    private final AmazonIdentityManagementClient iamClient;
    @Getter
    private AWSDatabase current;

    public AWSDatabaseHolder(Config config) {
        final BasicAWSCredentials awsCredentials = new BasicAWSCredentials(
                config.getString("accessKeyId"),
                config.getString("secretKeyId"));

        this.ec2Client = new AmazonEC2Client(awsCredentials);
        this.iamClient = new AmazonIdentityManagementClient(awsCredentials);

        rebuild();
    }

    public void rebuild() {
        current = new AWSDatabase(ec2Client, iamClient);
    }
}
