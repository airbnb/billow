package com.airbnb.billow;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.amazonaws.services.rds.model.DBCluster;
import com.amazonaws.services.rds.model.DBClusterMember;
import com.amazonaws.services.rds.model.DBInstance;
import com.amazonaws.services.rds.model.DBInstanceStatusInfo;
import com.amazonaws.services.rds.model.DBParameterGroupStatus;
import com.amazonaws.services.rds.model.DBSecurityGroupMembership;
import com.amazonaws.services.rds.model.DBSubnetGroup;
import com.amazonaws.services.rds.model.Endpoint;
import com.amazonaws.services.rds.model.OptionGroupMembership;
import com.amazonaws.services.rds.model.PendingModifiedValues;
import com.amazonaws.services.rds.model.Tag;
import com.amazonaws.services.rds.model.VpcSecurityGroupMembership;
import com.fasterxml.jackson.annotation.JsonFilter;

@Slf4j
@JsonFilter(RDSInstance.INSTANCE_FILTER)
public class RDSInstance {
    public static final String INSTANCE_FILTER = "InstanceFilter";

    @Getter
    private final Integer allocatedStorage;
    @Getter
    private final Boolean autoMinorVersionUpgrade;
    @Getter
    private final String availabilityZone;
    @Getter
    private final Integer backupRetentionPeriod;
    @Getter
    private final String characterSetName;
    @Getter
    private final String dBInstanceClass;
    @Getter
    private final String dBInstanceIdentifier;
    @Getter
    private final String dBInstanceStatus;
    @Getter
    private final String dBClusterIdentifier;
    @Getter
    private final String dBName;
    @Getter
    private final List<DBParameterGroupStatus> dBParameterGroups;
    @Getter
    private final List<DBSecurityGroupMembership> dBSecurityGroups;
    @Getter
    private final DBSubnetGroup dBSubnetGroup;
    @Getter
    private final Endpoint endpoint;
    @Getter
    private final String engine;
    @Getter
    private final String engineVersion;
    @Getter
    private final Date instanceCreateTime;
    @Getter
    private final Integer iops;
    @Getter
    private final Date latestRestorableTime;
    @Getter
    private final String licenseModel;
    @Getter
    private final String masterUsername;
    @Getter
    private final Boolean multiAZ;
    @Getter
    private final List<OptionGroupMembership> optionGroupMemberships;
    @Getter
    private final PendingModifiedValues pendingModifiedValues;
    @Getter
    private final String preferredBackupWindow;
    @Getter
    private final String preferredMaintenanceWindow;
    @Getter
    private final Boolean publiclyAccessible;
    @Getter
    private final List<String> readReplicaDBInstanceIdentifiers;
    @Getter
    private final String readReplicaSourceDBInstanceIdentifier;
    @Getter
    private final String secondaryAvailabilityZone;
    @Getter
    private final List<DBInstanceStatusInfo> statusInfos;
    @Getter
    private final List<VpcSecurityGroupMembership> vpcSecurityGroups;
    @Getter
    private final boolean isMaster;

    @Getter
    private final Map<String, String> tags;

    @Getter
    private final String privateIP;
    @Getter
    private final String hostname;

    @Getter
    private final List<String> snapshots;

    @Getter
    private final String caCertificateIdentifier;

    public RDSInstance(DBInstance instance, DBCluster cluster, List<Tag> tagList, List<String> snapshots) {
        this.allocatedStorage = instance.getAllocatedStorage();
        this.autoMinorVersionUpgrade = instance.getAutoMinorVersionUpgrade();
        this.availabilityZone = instance.getAvailabilityZone();
        this.backupRetentionPeriod = instance.getBackupRetentionPeriod();
        this.characterSetName = instance.getCharacterSetName();
        this.dBInstanceClass = instance.getDBInstanceClass();
        this.dBInstanceIdentifier = instance.getDBInstanceIdentifier();
        this.dBInstanceStatus = instance.getDBInstanceStatus();
        this.dBClusterIdentifier = instance.getDBClusterIdentifier();
        this.dBName = instance.getDBName();
        this.dBParameterGroups = instance.getDBParameterGroups();
        this.dBSecurityGroups = instance.getDBSecurityGroups();
        this.dBSubnetGroup = instance.getDBSubnetGroup();
        this.endpoint = instance.getEndpoint();
        if(this.endpoint != null) {
          this.hostname = endpoint.getAddress();
          this.privateIP = getPrivateIp(hostname);
        } else {
          this.hostname = null;
          this.privateIP = null;
        }
        this.engine = instance.getEngine();
        this.engineVersion = instance.getEngineVersion();
        this.instanceCreateTime = instance.getInstanceCreateTime();
        this.iops = instance.getIops();
        this.latestRestorableTime = instance.getLatestRestorableTime();
        this.licenseModel = instance.getLicenseModel();
        this.masterUsername = instance.getMasterUsername();
        this.multiAZ = instance.getMultiAZ();
        this.optionGroupMemberships = instance.getOptionGroupMemberships();
        this.pendingModifiedValues = instance.getPendingModifiedValues();
        this.preferredBackupWindow = instance.getPreferredBackupWindow();
        this.preferredMaintenanceWindow = instance.getPreferredMaintenanceWindow();
        this.publiclyAccessible = instance.getPubliclyAccessible();
        this.readReplicaDBInstanceIdentifiers = instance.getReadReplicaDBInstanceIdentifiers();
        this.readReplicaSourceDBInstanceIdentifier = instance.getReadReplicaSourceDBInstanceIdentifier();
        this.secondaryAvailabilityZone = instance.getSecondaryAvailabilityZone();
        this.statusInfos = instance.getStatusInfos();
        this.vpcSecurityGroups = instance.getVpcSecurityGroups();
        this.isMaster = checkIfMaster(instance, cluster);

        this.tags = new HashMap<>(tagList.size());
        for(Tag tag : tagList) {
            this.tags.put(tag.getKey(), tag.getValue());
        }

        this.snapshots = new ArrayList<>(snapshots);
        this.caCertificateIdentifier = instance.getCACertificateIdentifier();
    }

    public static boolean checkIfMaster(DBInstance instance, DBCluster cluster) {
        if (instance.getDBClusterIdentifier() == null || cluster == null) {
            // It's NOT a member of a DB cluster
            return instance.getReadReplicaSourceDBInstanceIdentifier() == null;
        } else {
            // It's a member of a DB cluster
            for (DBClusterMember member : cluster.getDBClusterMembers()) {
                if (member.getDBInstanceIdentifier().equals(instance.getDBInstanceIdentifier()) && member.isClusterWriter()) {
                    return true;
                }
            }
            return false;
        }
    }

    private String getPrivateIp(String hostname) {
        try {
            InetAddress address = InetAddress.getByName(hostname); 
            return address.getHostAddress().toString();
        } catch (UnknownHostException e) {
            log.error("{}", e);
        } 
        return null;
    }

}
