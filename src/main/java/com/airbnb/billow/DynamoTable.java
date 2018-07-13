package com.airbnb.billow;

import com.amazonaws.services.dynamodbv2.model.GlobalSecondaryIndexDescription;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;

import com.amazonaws.services.dynamodbv2.document.Table;
import com.fasterxml.jackson.annotation.JsonFilter;
import org.joda.time.DateTime;

@JsonFilter(DynamoTable.TABLE_FILTER)
public class DynamoTable {
    public static final String TABLE_FILTER = "TableFilter";

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
    @Getter
    private final List<DynamoGSI> globalSecondaryIndexes;

    public DynamoTable(Table table) {
        table.describe();
        tableName = table.getTableName();
        attributeDefinitions = table.getDescription().getAttributeDefinitions().toString();
        tableStatus = table.getDescription().getTableStatus();
        keySchema = table.getDescription().getKeySchema().toString();
        creationDateTime = new DateTime(table.getDescription().getCreationDateTime());
        numberOfDecreasesToday = table.getDescription().getProvisionedThroughput().getNumberOfDecreasesToday();
        readCapacityUnits = table.getDescription().getProvisionedThroughput().getReadCapacityUnits();
        writeCapacityUnits = table.getDescription().getProvisionedThroughput().getWriteCapacityUnits();
        tableSizeBytes = table.getDescription().getTableSizeBytes();
        itemCount = table.getDescription().getItemCount();
        tableArn = table.getDescription().getTableArn();
        provisionedThroughput = table.getDescription().getProvisionedThroughput().toString();
        globalSecondaryIndexes = new ArrayList<>();

        if (table.getDescription().getGlobalSecondaryIndexes() != null) {
            for (GlobalSecondaryIndexDescription gsiDesc : table
                .getDescription()
                .getGlobalSecondaryIndexes()) {
                globalSecondaryIndexes.add(new DynamoGSI(gsiDesc));
            }
        }
    }

    private static final class DynamoGSI {
        @Getter
        private final String gsiName;
        @Getter
        private final Long readCapacityUnits;
        @Getter
        private final Long writeCapacityUnits;
        @Getter
        private final Long itemCount;
        @Getter
        private final Long indexSizeBytes;
        @Getter
        private final String indexStatus;
        @Getter
        private final Boolean backfilling;
        @Getter
        private final String indexArn;

        public DynamoGSI(GlobalSecondaryIndexDescription desc) {
            gsiName = desc.getIndexName();
            readCapacityUnits = desc.getProvisionedThroughput().getReadCapacityUnits();
            writeCapacityUnits = desc.getProvisionedThroughput().getWriteCapacityUnits();
            itemCount = desc.getItemCount();
            indexSizeBytes = desc.getIndexSizeBytes();
            indexStatus = desc.getIndexStatus();
            backfilling = desc.getBackfilling();
            indexArn = desc.getIndexArn();
        }
    }
}
