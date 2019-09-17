# billow: inspect the cloud #

**As-is:** This project is not actively maintained or supported.
While updates may still be made and we welcome feedback, keep in mind we may not respond to pull
requests or issues quickly.

**Let us know!** If you fork this, or if you use it, or if it helps in anyway, we'd love to hear
from you! opensource@airbnb.com

## Goal ##

Query AWS data without API credentials. Don't wait for a response.

## Examples ##

Please look at
[Billow in action](https://github.com/airbnb/billow/wiki/Billow-in-action) in the Wiki
and start contributing!

    $ curl -sf billow.d.musta.ch/ec2 | jq '.[] | .az'
    $ curl -sf "billow.d.musta.ch/ec2?q=$(urlencode 'key=="igor"')" |jq '.[]|.daysOld' | statistik
    $ curl -sf 'billow.d.musta.ch/ec2?f=type' | jq -r '.[]|.type' | suc -r

## API ##

*The API is in a very early stage. Everything is subject to change.*

### Optional parameters ###
The `ec2`, `dynamo`, `sqs` and `elasticache` queries support following optional parameters:
- `q` / `query`: OGNL expression used to filter. Example:
  - `id>100&&readCapacityUnits>10`
- `s` / `sort`: OGNL expression used as a ``Comparable`` to sort (default to no ordering). Example:
  - `s=itemCount`
- `l` / `limit`: maximum number of records to return. Example:
  - `l=10`
- `f` / `field`: comma-separated list of fields to display(defaults to all). Examples:
  - `f=tableName,tableStatus`
  - `f=tableName`

Sample query: show readCapacityUnits and tableName of all tables sorted by readCapacity:
``/dynamo?s=readCapacityUnits&&f=readCapacityUnits,tableName&&l=3``

Sample response:
```
[
  {
    tableName: "table_foo",
    readCapacityUnits: 1
  },
  {
    tableName: "table_bar",
    readCapacityUnits: 2
  },
  {
    tableName: "table_baz",
    readCapacityUnits: 3
  }
]
```

### EC2 Query ###

#### /ec2/sg ####
List all ec2 security groups.

#### /ec2/all ####
List all ec2 instances.

#### /ec2 ####
Search ec2 instances with optional parameters.

Fields of ec2 instances:
```
{
  id: "id_foo",
  type: "m1.xlarge",
  lifecycle: null,
  hypervisor: "xen",
  az: "eu-west-1a",
  group: "",
  tenancy: "default",
  platform: null,
  kernel: "aki-62695816",
  key: "ops.2011-12-21",
  image: "ami-81c5fdf5",
  privateIP: null,
  publicIP: null,
  publicHostname: "",
  privateHostname: "",
  architecture: "x86_64",
  state: "stopped",
  ramdisk: null,
  subnet: null,
  rootDeviceName: "/dev/sda1",
  rootDeviceType: "ebs",
  stateTransitionReason: "User initiated",
  spotInstanceRequest: null,
  virtualizationType: "paravirtual",
  sourceDestCheck: null,
  stateReason: "Server.ScheduledStop: Stopped due to scheduled retirement",
  vpc: null,
  tags: {
    Name: "roambi"
  },
  launchTime: 1341250030000,
  securityGroups: [
    {
      id: "sg-4e20d876",
      name: "Open Web Server"
    }
  ],
  iamInstanceProfile: "arn:aws:iam::123456789012:instance-profile/sample-Iamrole",
  daysOld: 1352.2294
}
```

### RDS Query ###

#### /rds/all ####
List all RDS resources.

### DynamoDB Query ###

#### /dynamo ####
Search tables in DynamoDB with optional parameters.

Fields(``readCapacityUnits`` and ``writeCapacityUnits`` are pulled out of
  ``provisionedThroughput`` which allows users to sort with these attributes):

```
{
  "tableName": "table_foo",
  "attributeDefinitions": "[{AttributeName: foo,AttributeType: S}]",
  "tableStatus": "ACTIVE",
  "keySchema": "[{AttributeName: foo,KeyType: HASH}]",
  "creationDateTime":  1457475410,
  "numberOfDecreasesToday": 0,
  "readCapacityUnits": 1,
  "writeCapacityUnits": 1,
  "tableSizeBytes": 0,
  "itemCount": 0,
  "tableArn": "arn:aws:dynamodb:us-east-1:user_id:table/table_foo,
  "provisionedThroughput": "{NumberOfDecreasesToday: 0,ReadCapacityUnits: 1,
    WriteCapacityUnits: 1}"
}
```

### SQS Query ###

#### /sqs ####

Search queues in SQS with optional parameters.

Fields of SQS queues:
```
{
    url: "https://sqs.us-east-1.amazonaws.com/test_account/db1",
    approximateNumberOfMessagesDelayed: 0,
    receiveMessageWaitTimeSeconds: 0,
    createdTimestamp: 1455227837,
    delaySeconds: 0,
    messageRetentionPeriod: 1209600,
    maximumMessageSize: 262144,
    visibilityTimeout: 600,
    approximateNumberOfMessages: 0,
    lastModifiedTimestamp: 1457640519,
    queueArn: "arn:aws:sqs:us-east-1:test_account:db1"
}
```

### Elasticache Query ###

#### /elasticache/cluster ####

Search provisioned cache clusters with optional parameters.

Fields of elasticache clusters:
```
{
    cacheClusterId: "cluster_foo",
    configurationEndpoint: null,
    cacheNodeType: "cache.m1.small",
    engine: "memcached",
    engineVersion: "1.4.14",
    cacheClusterStatus: "available",
    numCacheNodes: 1,
    preferredAvailabilityZone: "us-east-1e",
    cacheClusterCreateTime: 1378321573582,
    preferredMaintenanceWindow: "fri:05:00-fri:07:00",
    pendingModifiedValues: "{CacheNodeIdsToRemove: [],}",
    notificationConfiguration: "{TopicArn: arn:aws:sns:us-east-1:test_account:db1,TopicStatus: active}",
    cacheSecurityGroups: "[]",
    cacheParameterGroup: "{CacheParameterGroupName: default.memcached1.4,ParameterApplyStatus: in-sync,CacheNodeIdsToReboot: []}",
    cacheSubnetGroupName: null,
    cacheNodes: "[]",
    autoMinorVersionUpgrade: true,
    securityGroups: "[]",
    replicationGroupId: null,
    snapshotRetentionLimit: null,
    snapshotWindow: null
}
```

### Elasticsearch Query ###

#### /elasticsearch ####
Fields of elasticsearch domains:
```
```

### IAM Query ###

#### /iam ####

List IAM user credentials.

## Configuration ##

### AWS ###

We strongly recommend creating a dedicated IAM user.

Here is the required User Policy:

    {
      "Statement": [
        { "Action": [
            "ec2:DescribeRegions",
            "ec2:DescribeInstanceAttribute",
            "ec2:DescribeInstanceStatus",
            "ec2:DescribeInstances",
            "ec2:DescribeSecurityGroups",
            "rds:DescribeDBInstances",
            "iam:GetUser",
            "iam:ListUsers",
            "iam:ListAccessKeys"
          ],
          "Effect": "Allow",
          "Resource": [ "*" ]
        }
      ]
    }


### Local configuration ###

We use Typesafe's config.
Look at the `reference.conf` resource for the available parameters.
To get quickly started, pass the following parameters to the JVM: `-Dbillow.aws.accessKeyId=HELLO -Dbillow.aws.secretKeyId=WoRld`.

Note: For local development, ensure that you remove the `profile ` prefix from
profile names in `~/.aws/config` and `~/.aws/credentials` if you are using
those. This is due to a mis-match in what the AWS Python SDK expects and what this
version of the Java SDK expects. [More details here.](https://github.com/aws/aws-sdk-java/issues/1707)

## Monitoring guide ##

We expose an admin port offering metrics and health checks.
See http://127.0.0.1:8081/ by default.

## References ##

- OGNL: http://commons.apache.org/proper/commons-ognl/language-guide.html
