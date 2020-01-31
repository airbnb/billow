package com.airbnb.billow;

import com.amazonaws.services.elasticache.model.DescribeReplicationGroupsRequest;
import com.amazonaws.services.elasticache.model.DescribeReplicationGroupsResult;
import com.amazonaws.services.elasticache.model.NodeGroup;
import com.amazonaws.services.elasticache.model.NodeGroupMember;
import com.amazonaws.services.elasticache.model.ReplicationGroup;
import com.amazonaws.services.elasticsearch.model.DescribeElasticsearchDomainRequest;
import com.amazonaws.services.elasticsearch.model.DescribeElasticsearchDomainResult;
import java.util.LinkedList;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
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
import com.amazonaws.services.elasticache.model.DescribeCacheClustersRequest;
import com.amazonaws.services.elasticache.model.DescribeCacheClustersResult;
import com.amazonaws.services.elasticsearch.AWSElasticsearchClient;
import com.amazonaws.services.elasticsearch.model.DomainInfo;
import com.amazonaws.services.elasticsearch.model.ListDomainNamesRequest;
import com.amazonaws.services.elasticsearch.model.ListDomainNamesResult;
import com.amazonaws.services.elasticsearch.model.ListTagsRequest;
import com.amazonaws.services.elasticsearch.model.ListTagsResult;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagement;
import com.amazonaws.services.identitymanagement.model.AccessKeyMetadata;
import com.amazonaws.services.identitymanagement.model.ListAccessKeysRequest;
import com.amazonaws.services.identitymanagement.model.ListUsersRequest;
import com.amazonaws.services.identitymanagement.model.ListUsersResult;
import com.amazonaws.services.identitymanagement.model.User;
import com.amazonaws.services.rds.AmazonRDSClient;
import com.amazonaws.services.rds.model.DBCluster;
import com.amazonaws.services.rds.model.DBClusterMember;
import com.amazonaws.services.rds.model.DBClusterSnapshot;
import com.amazonaws.services.rds.model.DBInstance;
import com.amazonaws.services.rds.model.DBSnapshot;
import com.amazonaws.services.rds.model.DescribeDBClusterSnapshotsRequest;
import com.amazonaws.services.rds.model.DescribeDBClusterSnapshotsResult;
import com.amazonaws.services.rds.model.DescribeDBClustersRequest;
import com.amazonaws.services.rds.model.DescribeDBClustersResult;
import com.amazonaws.services.rds.model.DescribeDBInstancesRequest;
import com.amazonaws.services.rds.model.DescribeDBInstancesResult;
import com.amazonaws.services.rds.model.DescribeDBSnapshotsRequest;
import com.amazonaws.services.rds.model.DescribeDBSnapshotsResult;
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
    private final ImmutableMultimap<String, ElasticsearchCluster> elasticsearchClusters;
    private final ImmutableList<IAMUserWithKeys> iamUsers;
    private final long timestamp;
    private String awsAccountNumber;
    private String awsARNPartition;

    AWSDatabase(final Map<String, AmazonEC2Client> ec2Clients,
                final Map<String, AmazonRDSClient> rdsClients,
                final Map<String, AmazonDynamoDBClient> dynamoClients,
                final Map<String, AmazonSQSClient> sqsClients,
                final Map<String, AmazonElastiCacheClient> elasticacheClients,
                final Map<String, AWSElasticsearchClient> elasticsearchClients,
                final AmazonIdentityManagement iamClient,
                final String configAWSAccountNumber,
                final String configAWSARNPartition) {
        timestamp = System.currentTimeMillis();
        log.info("Building AWS DB with timestamp {}", timestamp);

        log.info("Getting EC2 instances");
        final ImmutableMultimap.Builder<String, EC2Instance> ec2InstanceBuilder = new ImmutableMultimap.Builder<>();
        final ImmutableMultimap.Builder<String, DynamoTable> dynamoTableBuilder = new ImmutableMultimap.Builder<>();
        final ImmutableMultimap.Builder<String, SQSQueue> sqsQueueBuilder = new ImmutableMultimap.Builder<>();
        final ImmutableMultimap.Builder<String, ElasticacheCluster> elasticacheClusterBuilder =
            new ImmutableMultimap.Builder<>();
        final ImmutableMultimap.Builder<String, ElasticsearchCluster> elasticsearchClusterBuilder =
            new ImmutableMultimap.Builder<>();

        if (configAWSAccountNumber == null) {
            awsAccountNumber = "";
        } else {
            log.info("using account number '{}' from config", configAWSAccountNumber);
            awsAccountNumber = configAWSAccountNumber;
        }

        if (configAWSARNPartition == null) {
            awsARNPartition = "aws";
        } else {
            log.info("using arn partition '{}' from config", configAWSARNPartition);
            awsARNPartition = configAWSARNPartition;
        }

        /*
         * IAM keys
         * Put this in the beginning to populate the awsAccountNumber.
         */

        log.info("Getting IAM keys");
        final ImmutableList.Builder<IAMUserWithKeys> usersBuilder = new ImmutableList.Builder<>();

        final ListUsersRequest listUsersRequest = new ListUsersRequest();
        ListUsersResult listUsersResult;
        do {
            log.debug("Performing IAM request: {}", listUsersRequest);
            listUsersResult = iamClient.listUsers(listUsersRequest);
            final List<User> users = new LinkedList<>(); //listUsersResult.getUsers();
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
         * ElasticCache
         */

        for (Map.Entry<String, AmazonElastiCacheClient> clientPair : elasticacheClients.entrySet()) {
            final String regionName = clientPair.getKey();
            final AmazonElastiCacheClient client = clientPair.getValue();
            final Map<String, NodeGroupMember> clusterIdToNodeGroupMember = new HashMap<>();
            DescribeCacheClustersRequest describeCacheClustersRequest = new DescribeCacheClustersRequest();
            DescribeReplicationGroupsRequest describeReplicationGroupsRequest = new DescribeReplicationGroupsRequest();
            DescribeCacheClustersResult describeCacheClustersResult;
            DescribeReplicationGroupsResult describeReplicationGroupsResult;

            do {
                log.info("Getting Elasticache replication groups from {} with marker {}", regionName, describeReplicationGroupsRequest.getMarker());

                describeReplicationGroupsResult = client.describeReplicationGroups(describeReplicationGroupsRequest);

                for (ReplicationGroup replicationGroup: describeReplicationGroupsResult.getReplicationGroups()) {
                    for (NodeGroup nodeGroup: replicationGroup.getNodeGroups()) {
                        for (NodeGroupMember nodeGroupMember: nodeGroup.getNodeGroupMembers()) {
                            clusterIdToNodeGroupMember.put(nodeGroupMember.getCacheClusterId(), nodeGroupMember);
                        }
                    }
                }

                describeReplicationGroupsRequest.setMarker(describeReplicationGroupsResult.getMarker());
            } while (describeReplicationGroupsResult.getMarker() != null);

            do {
                log.info("Getting Elasticache from {} with marker {}", regionName, describeCacheClustersRequest.getMarker());

                describeCacheClustersResult = client.describeCacheClusters(describeCacheClustersRequest);
                int cntClusters = 0;

                for (CacheCluster cluster : describeCacheClustersResult.getCacheClusters()) {
                    com.amazonaws.services.elasticache.model.ListTagsForResourceRequest tagsRequest =
                        new com.amazonaws.services.elasticache.model.ListTagsForResourceRequest()
                            .withResourceName(elasticacheARN(awsARNPartition, regionName, awsAccountNumber, cluster));

                    com.amazonaws.services.elasticache.model.ListTagsForResourceResult tagsResult =
                        client.listTagsForResource(tagsRequest);
                    elasticacheClusterBuilder.putAll(regionName, new ElasticacheCluster(cluster, clusterIdToNodeGroupMember.get(cluster.getCacheClusterId()), tagsResult.getTagList()));
                    cntClusters++;
                }

                log.debug("Found {} cache clusters in {}", cntClusters, regionName);

                describeCacheClustersRequest.setMarker(describeCacheClustersResult.getMarker());
            } while (describeCacheClustersResult.getMarker() != null);


        }
        this.elasticacheClusters = elasticacheClusterBuilder.build();

        /*
         * Elasticsearch
         */

        for (Map.Entry<String, AWSElasticsearchClient> clientPair : elasticsearchClients.entrySet()) {
            final String regionName = clientPair.getKey();
            final AWSElasticsearchClient client = clientPair.getValue();
            ListDomainNamesRequest domainNamesRequest = new ListDomainNamesRequest();
            ListDomainNamesResult domainNamesResult = client.listDomainNames(domainNamesRequest);

            List<DomainInfo> domainInfoList = domainNamesResult.getDomainNames();
            for (DomainInfo domainInfo : domainInfoList) {
                ListTagsRequest listTagsRequest = new ListTagsRequest();
                listTagsRequest.setARN(elasticsearchARN(awsARNPartition, regionName, awsAccountNumber, domainInfo.getDomainName()));
                ListTagsResult tagList = client.listTags(listTagsRequest);

                DescribeElasticsearchDomainRequest describeDomainRequest = new DescribeElasticsearchDomainRequest();
                describeDomainRequest.setDomainName(domainInfo.getDomainName());
                DescribeElasticsearchDomainResult describeDomainResult = client.describeElasticsearchDomain(describeDomainRequest);

                elasticsearchClusterBuilder.putAll(regionName, new ElasticsearchCluster(describeDomainResult.getDomainStatus(), tagList.getTagList()));
            }
            log.debug("Found {} Elasticsearch domains in {}", domainInfoList.size(), regionName);

        }
        this.elasticsearchClusters = elasticsearchClusterBuilder.build();

        /*
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

        /*
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
         * RDS Instances
         */

        log.info("Getting RDS instances and clusters");
        final ImmutableMultimap.Builder<String, RDSInstance> rdsBuilder = new ImmutableMultimap.Builder<String, RDSInstance>();

        for (Map.Entry<String, AmazonRDSClient> clientPair : rdsClients.entrySet()) {
            final String regionName = clientPair.getKey();
            final AmazonRDSClient client = clientPair.getValue();
            final Map<String, DBCluster> instanceIdToCluster = new HashMap<>();

            DescribeDBClustersRequest dbClustersRequest = new DescribeDBClustersRequest();
            DescribeDBClustersResult clustersResult;

            log.info("Getting RDS clusters from {}", regionName);

            do {
                log.debug("Performing RDS request: {}", dbClustersRequest);
                clustersResult = client.describeDBClusters(dbClustersRequest);
                final List<DBCluster> clusters = clustersResult.getDBClusters();
                log.debug("Found {} DB clusters", clusters.size());
                for (DBCluster cluster : clusters) {
                    for (DBClusterMember member : cluster.getDBClusterMembers()) {
                        instanceIdToCluster.put(member.getDBInstanceIdentifier(), cluster);
                    }
                }
                dbClustersRequest.setMarker(clustersResult.getMarker());
            } while (clustersResult.getMarker() != null);

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
                            .withResourceName(rdsARN(awsARNPartition, regionName, awsAccountNumber, instance));

                    ListTagsForResourceResult tagsResult = client.listTagsForResource(tagsRequest);

                    List<String> snapshots = new ArrayList<>();
                    // Get snapshot for masters only.
                    if (RDSInstance.checkIfMaster(instance, instanceIdToCluster.get(instance.getDBInstanceIdentifier()))) {
                       if ("aurora".equals(instance.getEngine()) || "aurora-mysql".equals(instance.getEngine())) {
                           DescribeDBClusterSnapshotsRequest snapshotsRequest = new DescribeDBClusterSnapshotsRequest()
                               .withDBClusterIdentifier(instance.getDBClusterIdentifier());
                           DescribeDBClusterSnapshotsResult snapshotsResult = client.describeDBClusterSnapshots(snapshotsRequest);
                           for (DBClusterSnapshot s : snapshotsResult.getDBClusterSnapshots()) {
                               snapshots.add(s.getDBClusterSnapshotIdentifier());
                           }
                       } else {
                           DescribeDBSnapshotsRequest snapshotsRequest = new DescribeDBSnapshotsRequest()
                               .withDBInstanceIdentifier(instance.getDBInstanceIdentifier());
                           DescribeDBSnapshotsResult snapshotsResult = client.describeDBSnapshots(snapshotsRequest);
                           for (DBSnapshot s : snapshotsResult.getDBSnapshots()) {
                               snapshots.add(s.getDBSnapshotIdentifier());
                           }
                       }
                    }
                    rdsBuilder.putAll(regionName, new RDSInstance(instance,
                        instanceIdToCluster.get(instance.getDBInstanceIdentifier()), tagsResult.getTagList(), snapshots));

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

    private String rdsARN(String partition, String regionName, String accountNumber, DBInstance instance) {
        return String.format(
                "arn:%s:rds:%s:%s:db:%s",
                partition,
                regionName,
                accountNumber,
                instance.getDBInstanceIdentifier()
        );
    }

    private String elasticacheARN(String partition, String regionName, String accountNumber, CacheCluster cacheCluster) {
        return String.format(
                "arn:%s:elasticache:%s:%s:cluster:%s",
                partition,
                regionName,
                accountNumber,
                cacheCluster.getCacheClusterId()
        );
    }

    private String elasticsearchARN(String partition, String regionName, String accountNumber, String domainName) {
        return String.format(
                "arn:%s:es:%s:%s:domain/%s",
                partition,
                regionName,
                accountNumber,
                domainName
        );
    }
}
