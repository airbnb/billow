package com.airbnb.billow;

import lombok.Getter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.amazonaws.services.ec2.model.GroupIdentifier;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.StateReason;
import com.amazonaws.services.ec2.model.Tag;
import com.fasterxml.jackson.annotation.JsonFilter;
import org.joda.time.DateTime;
import org.joda.time.Interval;

@JsonFilter(EC2Instance.INSTANCE_FILTER)
public class EC2Instance {
    public static final String INSTANCE_FILTER = "InstanceFilter";

    @Getter
    private final String id;
    @Getter
    private final String type;
    @Getter
    private final String lifecycle;
    @Getter
    private final String hypervisor;
    @Getter
    private final String az;
    @Getter
    private final String group;
    @Getter
    private final String tenancy;
    @Getter
    private final String platform;
    @Getter
    private final String kernel;
    @Getter
    private final String key;
    @Getter
    private final String image;
    @Getter
    private final String privateIP;
    @Getter
    private final String publicIP;
    @Getter
    private final String publicHostname;
    @Getter
    private final String privateHostname;
    @Getter
    private final String architecture;
    @Getter
    private final String state;
    @Getter
    private final String ramdisk;
    @Getter
    private final String subnet;
    @Getter
    private final String rootDeviceName;
    @Getter
    private final String rootDeviceType;
    @Getter
    private final String stateTransitionReason;
    @Getter
    private final String spotInstanceRequest;
    @Getter
    private final String virtualizationType;
    @Getter
    private final Boolean sourceDestCheck;
    @Getter
    private final String stateReason;
    @Getter
    private final String vpc;
    @Getter
    private final Map<String, String> tags;
    @Getter
    private final DateTime launchTime;
    @Getter
    private final List<SecurityGroup> securityGroups;
    @Getter
    private final String iamInstanceProfile;

    public EC2Instance(Instance instance) {
        this.id = instance.getInstanceId();
        this.type = instance.getInstanceType();
        this.lifecycle = instance.getInstanceLifecycle();
        this.hypervisor = instance.getHypervisor();
        this.az = instance.getPlacement().getAvailabilityZone();
        this.group = instance.getPlacement().getGroupName();
        this.tenancy = instance.getPlacement().getTenancy();
        this.vpc = instance.getVpcId();
        this.platform = instance.getPlatform();
        this.kernel = instance.getKernelId();
        this.key = instance.getKeyName();
        this.image = instance.getImageId();
        this.privateIP = instance.getPrivateIpAddress();
        this.publicIP = instance.getPublicIpAddress();
        this.publicHostname = instance.getPublicDnsName();
        this.privateHostname = instance.getPrivateDnsName();
        this.architecture = instance.getArchitecture();
        this.state = instance.getState().getName();
        this.ramdisk = instance.getRamdiskId();
        this.subnet = instance.getSubnetId();
        this.rootDeviceName = instance.getRootDeviceName();
        this.rootDeviceType = instance.getRootDeviceType();
        this.stateTransitionReason = instance.getStateTransitionReason();
        this.spotInstanceRequest = instance.getSpotInstanceRequestId();
        this.virtualizationType = instance.getVirtualizationType();
        this.sourceDestCheck = instance.getSourceDestCheck();
        this.launchTime = new DateTime(instance.getLaunchTime());

        if (instance.getIamInstanceProfile() != null) {
            this.iamInstanceProfile = instance.getIamInstanceProfile().getArn().toString();
        } else {
            this.iamInstanceProfile = null;
        }

        final StateReason stateReason = instance.getStateReason();
        if (stateReason != null)
            this.stateReason = stateReason.getMessage();
        else
            this.stateReason = null;

        this.securityGroups = new ArrayList<>();
        for (GroupIdentifier identifier : instance.getSecurityGroups()) {
            this.securityGroups.add(new SecurityGroup(identifier));
        }

        this.tags = new HashMap<>();
        for (Tag tag : instance.getTags()) {
            this.tags.put(tag.getKey(), tag.getValue());
        }
    }

    public float getDaysOld() {
        return new Interval(this.launchTime, new DateTime()).toDurationMillis() / (1000.0f * 60.0f * 60.0f * 24.0f);
    }

    private static final class SecurityGroup {
        @Getter
        private final String id;
        @Getter
        private final String name;

        public SecurityGroup(GroupIdentifier id) {
            this.id = id.getGroupId();
            this.name = id.getGroupName();
        }
    }
}
