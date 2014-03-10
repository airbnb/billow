package com.airbnb.billow;

import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.SecurityGroup;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClient;
import com.amazonaws.services.identitymanagement.model.*;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
@Data
public class AWSDatabase {
    private final ImmutableList<EC2Instance> instances;
    private final ImmutableList<SecurityGroup> ec2SGs;
    private final ImmutableList<AccessKeyMetadata> accessKeyMetadata;
    private final ImmutableMap<String, EC2Instance> instancesById;
    private final long timestamp;

    AWSDatabase(List<AmazonEC2Client> ec2Clients, AmazonIdentityManagementClient iamClient) {
        timestamp = System.currentTimeMillis();
        log.info("Building AWS DB with timestamp {}", timestamp);

        log.info("Getting instances");
        final ImmutableList.Builder<EC2Instance> ec2InstanceBuilder = new ImmutableList.Builder<EC2Instance>();

        for (AmazonEC2Client client : ec2Clients) {
            AWSDatabase.log.info("Getting EC2 reservations from {}", client);
            final List<Reservation> reservations = client.describeInstances().getReservations();
            AWSDatabase.log.debug("Found {} reservations", reservations.size());
            for (Reservation reservation : reservations) {
                for (Instance instance : reservation.getInstances())
                    ec2InstanceBuilder.add(new EC2Instance(instance));
            }
        }

        this.instances = ec2InstanceBuilder.build();
        this.instancesById = Maps.uniqueIndex(this.instances, new Function<EC2Instance, String>() {
            public String apply(EC2Instance input) {
                return input.getId();
            }
        });

        log.info("Getting EC2 security groups");
        final ImmutableList.Builder<SecurityGroup> ec2SGbuilder = new ImmutableList.Builder<SecurityGroup>();
        for (AmazonEC2Client ec2Client : ec2Clients) {
            ec2SGbuilder.addAll(ec2Client.describeSecurityGroups().getSecurityGroups());
        }
        this.ec2SGs = ec2SGbuilder.build();

        log.info("Getting IAM keys");
        final ImmutableList.Builder<AccessKeyMetadata> keyMDBuilder = new ImmutableList.Builder<AccessKeyMetadata>();

        final ListUsersRequest listUsersRequest = new ListUsersRequest();
        ListUsersResult listUsersResult;
        do {
            log.debug("Performing AMI request: {}", listUsersRequest);
            listUsersResult = iamClient.listUsers(listUsersRequest);
            final List<User> users = listUsersResult.getUsers();
            log.debug("Found {} users", users.size());
            for (User user : users) {
                final ListAccessKeysRequest listAccessKeysRequest = new ListAccessKeysRequest();
                listAccessKeysRequest.setUserName(user.getUserName());
                final List<AccessKeyMetadata> accessKeyMetadata = iamClient.listAccessKeys(listAccessKeysRequest).getAccessKeyMetadata();
                keyMDBuilder.addAll(accessKeyMetadata);
            }
            listUsersRequest.setMarker(listUsersResult.getMarker());
        } while (listUsersResult.isTruncated());

        this.accessKeyMetadata = keyMDBuilder.build();

        log.info("Done building AWS DB");
    }

    public long getAgeInMs() {
        return System.currentTimeMillis() - getTimestamp();
    }
}
