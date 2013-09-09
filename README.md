# billow: inspect the cloud #

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

### /ec2 ###

Search EC2 instances.

Optional parameters:

- `q`/`query`: OGNL expression used to filter (defaults to all instances). Examples:

  - `state=="running" && key=="pierre" && launchTime > 1365000000000`

- `s`/`sort`: OGNL expression used as a `Comparable` to sort (default to no ordering). Example: `daysOld`.

- `l`/`limit`: maximum number of records to return (defaults to none). Example: `10`.

- `f`/`fields`: comma-separated list of fields to display (defaults to all). Examples:

  - `type`
  - `az,type`
  - `id,publicIP,launchTime`

### /iam ###

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

## Monitoring guide ##

We expose an admin port offering metrics and health checks.
See http://127.0.0.1:8081/ by default.

## References ##

- OGNL: http://commons.apache.org/proper/commons-ognl/language-guide.html
