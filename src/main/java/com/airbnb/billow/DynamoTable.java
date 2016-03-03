package com.airbnb.billow;

import lombok.Getter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.ec2.model.GroupIdentifier;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.StateReason;
import com.amazonaws.services.ec2.model.Tag;
import com.fasterxml.jackson.annotation.JsonFilter;
import org.joda.time.DateTime;
import org.joda.time.Interval;

@JsonFilter(DynamoTable.Table_FILTER)
public class DynamoTable {
    public static final String Table_FILTER = "TableFilter";

    @Getter
    private final String tableName;
    @Getter
    private final String attributeDefinitions;
    @Getter
    private final String tableStatus;
    @Getter
    private final String keySchema;
    @Getter
    private final DateTime creationDateTime;
    @Getter
    private final long numberOfDecreasesToday;
    @Getter
    private final long readCapacityUnits;
    @Getter
    private final long writeCapacityUnits;
    @Getter
    private final long tableSizeBytes;
    @Getter
    private final long itemCount;
    @Getter
    private final String tableArn;
    @Getter
    private final String provisionedThroughput;

    public DynamoTable(Table table) {
        tableName = table.getTableName();
        attributeDefinitions = table.describe().getAttributeDefinitions().toString();
        tableStatus = table.describe().getTableStatus();
        keySchema = table.describe().getKeySchema().toString();
        creationDateTime = new DateTime(table.describe().getCreationDateTime());
        numberOfDecreasesToday = table.describe().getProvisionedThroughput().getNumberOfDecreasesToday();
        readCapacityUnits = table.describe().getProvisionedThroughput().getReadCapacityUnits();
        writeCapacityUnits = table.describe().getProvisionedThroughput().getWriteCapacityUnits();
        tableSizeBytes = table.describe().getTableSizeBytes();
        itemCount = table.describe().getItemCount();
        tableArn = table.describe().getTableArn();
        provisionedThroughput = table.describe().getProvisionedThroughput().toString();
    }
}
