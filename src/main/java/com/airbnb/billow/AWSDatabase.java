package com.airbnb.billow;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.document.TableCollection;
import com.amazonaws.services.dynamodbv2.model.ListTablesResult;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.SecurityGroup;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClient;
import com.amazonaws.services.identitymanagement.model.AccessKeyMetadata;
import com.amazonaws.services.identitymanagement.model.ListAccessKeysRequest;
import com.amazonaws.services.identitymanagement.model.ListUsersRequest;
import com.amazonaws.services.identitymanagement.model.ListUsersResult;
import com.amazonaws.services.identitymanagement.model.User;
import com.amazonaws.services.rds.AmazonRDSClient;
import com.amazonaws.services.rds.model.DBInstance;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;

@Slf4j
@Data
public class AWSDatabase {
    private final ImmutableMultimap<String, EC2Instance> ec2Instances;
    private final ImmutableMultimap<String, DynamoTable> dynamoTables;
    // private final ImmutableMultimap<String, RDSInstance> rdsInstances;
    private final ImmutableMultimap<String, SecurityGroup> ec2SGs;
    private final ImmutableList<IAMUserWithKeys> iamUsers;
    private final long timestamp;
    private String awsAccountNumber;

    AWSDatabase(final Map<String, AmazonEC2Client> ec2Clients,
                final Map<String, AmazonRDSClient> rdsClients,
                final Map<String, AmazonDynamoDBClient> dynamoClients,
                final AmazonIdentityManagementClient iamClient) {
        this(ec2Clients, rdsClients, dynamoClients, iamClient, null);
    }

    AWSDatabase(final Map<String, AmazonEC2Client> ec2Clients,
                final Map<String, AmazonRDSClient> rdsClients,
                final Map<String, AmazonDynamoDBClient> dynamoClients,
                final AmazonIdentityManagementClient iamClient,
                final String configAWSAccountNumber) {
        timestamp = System.currentTimeMillis();
        log.info("Building AWS DB with timestamp {}", timestamp);

        log.info("Getting EC2 instances");
        final ImmutableMultimap.Builder<String, EC2Instance> ec2InstanceBuilder = new ImmutableMultimap.Builder<String, EC2Instance>();
        final ImmutableMultimap.Builder<String, DynamoTable> dynamoTableBuilder = new ImmutableMultimap.Builder<>();
        if (configAWSAccountNumber == null) {
            awsAccountNumber = "";
        } else {
            log.info("using account number '{}' from config", configAWSAccountNumber);
            awsAccountNumber = configAWSAccountNumber;
        }

        /**
         * DynamoDB Tables
         */

        for (Map.Entry<String, AmazonDynamoDBClient> clientPair : dynamoClients.entrySet()) {
            final String regionName = clientPair.getKey();
            final AmazonDynamoDBClient client = clientPair.getValue();
            final DynamoDB dynamoDB = new DynamoDB(client);
            TableCollection<ListTablesResult> tables = dynamoDB.listTables();
            Iterator<Table> iterator = tables.iterator();

            log.info("Getting DynamoDB from {}", regionName);
            int cnt = 0;
            while (iterator.hasNext()) {
                Table table = iterator.next();
                dynamoTableBuilder.putAll(regionName, new DynamoTable(table));
                cnt++;
            }

            log.debug("Found {} dynamodbs in {}", cnt, regionName);
        }
        this.dynamoTables = dynamoTableBuilder.build();

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
         * IAM keys
         */

        log.info("Getting IAM keys");
        final ImmutableList.Builder<IAMUserWithKeys> usersBuilder = new ImmutableList.Builder<IAMUserWithKeys>();

        final ListUsersRequest listUsersRequest = new ListUsersRequest();
        ListUsersResult listUsersResult;
        do {
            log.debug("Performing IAM request: {}", listUsersRequest);
            listUsersResult = iamClient.listUsers(listUsersRequest);
            final List<User> users = listUsersResult.getUsers();
            log.debug("Found {} users", users.size());
            for (User user : users) {
                final ListAccessKeysRequest listAccessKeysRequest = new ListAccessKeysRequest();
                listAccessKeysRequest.setUserName(user.getUserName());
                final List<AccessKeyMetadata> accessKeyMetadata = iamClient.listAccessKeys(listAccessKeysRequest).getAccessKeyMetadata();

                final IAMUserWithKeys userWithKeys = new IAMUserWithKeys(user, ImmutableList.<AccessKeyMetadata>copyOf(accessKeyMetadata));
                usersBuilder.add(userWithKeys);

                if (awsAccountNumber.isEmpty()) {
                    awsAccountNumber = user.getArn().split(":")[4];
                }
            }
            listUsersRequest.setMarker(listUsersResult.getMarker());
        } while (listUsersResult.isTruncated());
        this.iamUsers = usersBuilder.build();

        /*
         * RDS Instances
         */
        /**
        log.info("Getting RDS instances");
        final ImmutableMultimap.Builder<String, RDSInstance> rdsBuilder = new ImmutableMultimap.Builder<String, RDSInstance>();

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
                for (DBInstance instance : instances) {
                    ListTagsForResourceRequest tagsRequest = new ListTagsForResourceRequest()
                            .withResourceName(rdsARN(regionName, awsAccountNumber, instance));

                    ListTagsForResourceResult tagsResult = client.listTagsForResource(tagsRequest);

                    rdsBuilder.putAll(regionName, new RDSInstance(instance, tagsResult.getTagList()));

                }
                rdsRequest.setMarker(result.getMarker());
            } while (result.getMarker() != null);
        }
        this.rdsInstances = rdsBuilder.build();
        **/

        log.info("Done building AWS DB");
    }

    public long getAgeInMs() {
        return System.currentTimeMillis() - getTimestamp();
    }

    private String rdsARN(String regionName, String accountNumber, DBInstance instance) {
        return String.format(
                "arn:aws:rds:%s:%s:db:%s",
                regionName,
                accountNumber,
                instance.getDBInstanceIdentifier()
        );
    }
}
