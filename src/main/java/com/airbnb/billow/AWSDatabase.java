package com.airbnb.billow;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
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
import com.amazonaws.services.elasticache.AmazonElastiCacheClient;
import com.amazonaws.services.elasticache.model.CacheCluster;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClient;
import com.amazonaws.services.identitymanagement.model.AccessKeyMetadata;
import com.amazonaws.services.identitymanagement.model.ListAccessKeysRequest;
import com.amazonaws.services.identitymanagement.model.ListUsersRequest;
import com.amazonaws.services.identitymanagement.model.ListUsersResult;
import com.amazonaws.services.identitymanagement.model.User;
import com.amazonaws.services.rds.AmazonRDSClient;
import com.amazonaws.services.rds.model.DBInstance;
import com.amazonaws.services.rds.model.DBInstanceNotFoundException;
import com.amazonaws.services.rds.model.DescribeDBInstancesRequest;
import com.amazonaws.services.rds.model.DescribeDBInstancesResult;
import com.amazonaws.services.rds.model.ListTagsForResourceRequest;
import com.amazonaws.services.rds.model.ListTagsForResourceResult;
import com.amazonaws.services.sqs.AmazonSQSClient;
import com.amazonaws.services.sqs.model.ListQueuesResult;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;

@Slf4j
@Data
public class AWSDatabase {
    private final ImmutableMultimap<String, EC2Instance> ec2Instances;
    private final ImmutableMultimap<String, DynamoTable> dynamoTables;
    private final ImmutableMultimap<String, RDSInstance> rdsInstances;
    private final ImmutableMultimap<String, SecurityGroup> ec2SGs;
    private final ImmutableMultimap<String, SQSQueue> sqsQueues;
    private final ImmutableMultimap<String, ElasticacheCluster> elasticacheClusters;
    private final ImmutableList<IAMUserWithKeys> iamUsers;
    private final long timestamp;
    private String awsAccountNumber;

    AWSDatabase(final Map<String, AmazonEC2Client> ec2Clients,
                final Map<String, AmazonRDSClient> rdsClients,
                final Map<String, AmazonDynamoDBClient> dynamoClients,
                final Map<String, AmazonSQSClient> sqsClients,
                final Map<String, AmazonElastiCacheClient> elasticacheClients,
                final AmazonIdentityManagementClient iamClient) {
        this(ec2Clients, rdsClients, dynamoClients, sqsClients, elasticacheClients, iamClient, null);
    }

    AWSDatabase(final Map<String, AmazonEC2Client> ec2Clients,
                final Map<String, AmazonRDSClient> rdsClients,
                final Map<String, AmazonDynamoDBClient> dynamoClients,
                final Map<String, AmazonSQSClient> sqsClients,
                final Map<String, AmazonElastiCacheClient> elasticacheClients,
                final AmazonIdentityManagementClient iamClient,
                final String configAWSAccountNumber) {
        timestamp = System.currentTimeMillis();
        log.info("Building AWS DB with timestamp {}", timestamp);

        log.info("Getting EC2 instances");
        final ImmutableMultimap.Builder<String, EC2Instance> ec2InstanceBuilder = new ImmutableMultimap.Builder<>();
        final ImmutableMultimap.Builder<String, DynamoTable> dynamoTableBuilder = new ImmutableMultimap.Builder<>();
        final ImmutableMultimap.Builder<String, SQSQueue> sqsQueueBuilder = new ImmutableMultimap.Builder<>();
        final ImmutableMultimap.Builder<String, ElasticacheCluster> elasticacheClusterBuilder =
            new ImmutableMultimap.Builder<>();

        if (configAWSAccountNumber == null) {
            awsAccountNumber = "";
        } else {
            log.info("using account number '{}' from config", configAWSAccountNumber);
            awsAccountNumber = configAWSAccountNumber;
        }

        /**
         * ElasticCache
         */
        for (Map.Entry<String, AmazonElastiCacheClient> clientPair : elasticacheClients.entrySet()) {
            final String regionName = clientPair.getKey();
            final AmazonElastiCacheClient client = clientPair.getValue();

            List<CacheCluster> clusters = client.describeCacheClusters().getCacheClusters();


            log.info("Getting Elasticache from {}", regionName);
            int cntClusters = 0;

            for (CacheCluster cluster : clusters) {
                elasticacheClusterBuilder.putAll(regionName, new ElasticacheCluster(cluster));
                cntClusters++;
            }

            log.debug("Found {} cache clusters in {}", cntClusters, regionName);
        }
        this.elasticacheClusters = elasticacheClusterBuilder.build();

        /**
         * SQS Queues
         */

        for (Map.Entry<String, AmazonSQSClient> clientPair : sqsClients.entrySet()) {
            final String regionName = clientPair.getKey();
            final AmazonSQSClient client = clientPair.getValue();
            ListQueuesResult queues = client.listQueues();

            log.info("Getting SQS from {}", regionName);
            int cnt = 0;
            for (String url : queues.getQueueUrls()) {
                List<String> attrs = new ArrayList<>();
                attrs.add("All");

                Map<String, String> map = client.getQueueAttributes(url, attrs).getAttributes();
                String approximateNumberOfMessagesDelayed = map.get(SQSQueue.ATTR_APPROXIMATE_NUMBER_OF_MESSAGES_DELAYED);
                String receiveMessageWaitTimeSeconds = map.get(SQSQueue.ATTR_RECEIVE_MESSAGE_WAIT_TIME_SECONDS);
                String createdTimestamp = map.get(SQSQueue.ATTR_CREATED_TIMESTAMP);
                String delaySeconds = map.get(SQSQueue.ATTR_DELAY_SECONDS);
                String messageRetentionPeriod = map.get(SQSQueue.ATTR_MESSAGE_RETENTION_PERIOD);
                String maximumMessageSize = map.get(SQSQueue.ATTR_MAXIMUM_MESSAGE_SIZE);
                String visibilityTimeout = map.get(SQSQueue.ATTR_VISIBILITY_TIMEOUT);
                String approximateNumberOfMessages = map.get(SQSQueue.ATTR_APPROXIMATE_NUMBER_OF_MESSAGES);
                String lastModifiedTimestamp = map.get(SQSQueue.ATTR_LAST_MODIFIED_TIMESTAMP);
                String queueArn = map.get(SQSQueue.ATTR_QUEUE_ARN);

                SQSQueue queue = new SQSQueue(url, Long.valueOf(approximateNumberOfMessagesDelayed),
                    Long.valueOf(receiveMessageWaitTimeSeconds), Long.valueOf(createdTimestamp),
                    Long.valueOf(delaySeconds), Long.valueOf(messageRetentionPeriod), Long.valueOf(maximumMessageSize),
                    Long.valueOf(visibilityTimeout), Long.valueOf(approximateNumberOfMessages),
                    Long.valueOf(lastModifiedTimestamp), queueArn);

                sqsQueueBuilder.putAll(regionName, queue);
                cnt++;
            }

            log.debug("Found {} queues in {}", cnt, regionName);
        }
        this.sqsQueues = sqsQueueBuilder.build();

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
        final ImmutableList.Builder<IAMUserWithKeys> usersBuilder = new ImmutableList.Builder<>();

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

                    try {
                        ListTagsForResourceResult tagsResult = client.listTagsForResource(tagsRequest);

                        rdsBuilder.putAll(regionName, new RDSInstance(instance, tagsResult.getTagList()));
                    } catch(DBInstanceNotFoundException e) {
                        // It is possible for an instance to disappear between when we got the list of instances and
                        // when we go to find the instance's tags.
                        log.warn("Unable to find RDS instance '" +
                                instance.getDBInstanceIdentifier() +
                                "', last known status was '" +
                                instance.getDBInstanceStatus() +
                                "'.  Exception: " + e.toString());
                    }
                }
                rdsRequest.setMarker(result.getMarker());
            } while (result.getMarker() != null);
        }
        this.rdsInstances = rdsBuilder.build();

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
