package com.airbnb.billow;

import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.SecurityGroup;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClient;
import com.amazonaws.services.identitymanagement.model.*;
import com.amazonaws.services.rds.AmazonRDSClient;
import com.amazonaws.services.rds.model.DBInstance;
import com.amazonaws.services.rds.model.DescribeDBInstancesRequest;
import com.amazonaws.services.rds.model.DescribeDBInstancesResult;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

@Slf4j
@Data
public class AWSDatabase {
    private final ImmutableMultimap<String, EC2Instance> ec2Instances;
    private final ImmutableMultimap<String, DBInstance> rdsInstances;
    private final ImmutableMultimap<String, SecurityGroup> ec2SGs;
    private final ImmutableList<IAMUserWithKeys> iamUsers;
    private final long timestamp;

    AWSDatabase(final Map<String, AmazonEC2Client> ec2Clients,
                final Map<String, AmazonRDSClient> rdsClients,
                final AmazonIdentityManagementClient iamClient) {
        timestamp = System.currentTimeMillis();
        log.info("Building AWS DB with timestamp {}", timestamp);

        log.info("Getting EC2 instances");
        final ImmutableMultimap.Builder<String, EC2Instance> ec2InstanceBuilder = new ImmutableMultimap.Builder<String, EC2Instance>();

        /*
         * EC2 Instances
         */

        for (Map.Entry<String, AmazonEC2Client> clientPair : ec2Clients.entrySet()) {
            final String regionName = clientPair.getKey();
            final AmazonEC2Client client = clientPair.getValue();

            log.info("Getting EC2 reservations from {}", regionName);

            final List<Reservation> reservations = client.describeInstances().getReservations();
            log.debug("Found {} reservations in {}", reservations.size(), regionName);
            for (Reservation reservation : reservations) {
                for (Instance instance : reservation.getInstances())
                    ec2InstanceBuilder.putAll(regionName, new EC2Instance(instance));
            }
        }
        this.ec2Instances = ec2InstanceBuilder.build();

        /*
         * EC2 security groups
         */

        log.info("Getting EC2 security groups");
        final ImmutableMultimap.Builder<String, SecurityGroup> ec2SGbuilder = new ImmutableMultimap.Builder<String, SecurityGroup>();
        for (Map.Entry<String, AmazonEC2Client> clientPair : ec2Clients.entrySet()) {
            final String regionName = clientPair.getKey();
            final AmazonEC2Client client = clientPair.getValue();
            final List<SecurityGroup> securityGroups = client.describeSecurityGroups().getSecurityGroups();
            log.debug("Found {} security groups in {}", securityGroups.size(), regionName);
            ec2SGbuilder.putAll(regionName, securityGroups);
        }
        this.ec2SGs = ec2SGbuilder.build();

        /*
         * RDS Instances
         */

        log.info("Getting RDS instances");
        final ImmutableMultimap.Builder<String, DBInstance> rdsBuilder = new ImmutableMultimap.Builder<String, DBInstance>();

        for (Map.Entry<String, AmazonRDSClient> clientPair : rdsClients.entrySet()) {
            final String regionName = clientPair.getKey();
            final AmazonRDSClient client = clientPair.getValue();

            DescribeDBInstancesRequest rdsRequest = new DescribeDBInstancesRequest();
            DescribeDBInstancesResult result;

            log.info("Getting RDS instances from {}", regionName);

            do {
                log.debug("Performing RDS request: {}", rdsRequest);
                result = client.describeDBInstances(rdsRequest);
                final List<DBInstance> instances = result.getDBInstances();
                log.debug("Found {} RDS instances", instances.size());
                rdsBuilder.putAll(regionName, instances);
                rdsRequest.setMarker(rdsRequest.getMarker());
            } while (result.getMarker() != null);
        }
        this.rdsInstances = rdsBuilder.build();

        /*
         * IAM keys
         */

//        log.info("Getting IAM keys");
//        final ImmutableList.Builder<IAMUserWithKeys> usersBuilder = new ImmutableList.Builder<IAMUserWithKeys>();
//
//        final ListUsersRequest listUsersRequest = new ListUsersRequest();
//        ListUsersResult listUsersResult;
//        do {
//            log.debug("Performing AMI request: {}", listUsersRequest);
//            listUsersResult = iamClient.listUsers(listUsersRequest);
//            final List<User> users = listUsersResult.getUsers();
//            log.debug("Found {} users", users.size());
//            for (User user : users) {
//                final ListAccessKeysRequest listAccessKeysRequest = new ListAccessKeysRequest();
//                listAccessKeysRequest.setUserName(user.getUserName());
//                final List<AccessKeyMetadata> accessKeyMetadata = iamClient.listAccessKeys(listAccessKeysRequest).getAccessKeyMetadata();
//
//                final IAMUserWithKeys userWithKeys = new IAMUserWithKeys(user, ImmutableList.<AccessKeyMetadata>copyOf(accessKeyMetadata));
//                usersBuilder.add(userWithKeys);
//            }
//            listUsersRequest.setMarker(listUsersResult.getMarker());
//        } while (listUsersResult.isTruncated());
//        this.iamUsers = usersBuilder.build();

        log.info("Done building AWS DB");
    }

    public long getAgeInMs() {
        return System.currentTimeMillis() - getTimestamp();
    }
}
