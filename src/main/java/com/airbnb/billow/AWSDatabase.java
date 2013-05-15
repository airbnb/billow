package com.airbnb.billow;

import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClient;
import com.amazonaws.services.identitymanagement.model.AccessKeyMetadata;
import com.amazonaws.services.identitymanagement.model.ListAccessKeysRequest;
import com.amazonaws.services.identitymanagement.model.User;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
public class AWSDatabase {
    private final ImmutableList<EC2Instance> instances;
    private final ImmutableList<AccessKeyMetadata> accessKeyMetadata;
    private final ImmutableMap<String, EC2Instance> instancesById;

    AWSDatabase(AmazonEC2Client client, AmazonIdentityManagementClient iamClient) {
        log.info("Building AWS DB");

        log.debug("Getting instances");
        final ImmutableList.Builder<EC2Instance> builder = new ImmutableList.Builder<EC2Instance>();
        for (Reservation reservation : client.describeInstances().getReservations())
            for (Instance instance : reservation.getInstances())
                builder.add(new EC2Instance(instance));

        this.instances = builder.build();
        this.instancesById = Maps.uniqueIndex(this.instances, new Function<EC2Instance, String>() {
            public String apply(EC2Instance input) {
                return input.getId();
            }
        });

        log.debug("Getting IAM keys");
        final ImmutableList.Builder<AccessKeyMetadata> keyMDBuilder = new ImmutableList.Builder<AccessKeyMetadata>();
        for (User user : iamClient.listUsers().getUsers()) {
            final ListAccessKeysRequest req = new ListAccessKeysRequest();
            req.setUserName(user.getUserName());
            keyMDBuilder.addAll(iamClient.listAccessKeys(req).getAccessKeyMetadata());
        }
        this.accessKeyMetadata = keyMDBuilder.build();

        log.info("Done building AWS DB");
    }
}
