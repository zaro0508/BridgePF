package org.sagebionetworks.bridge.dynamodb;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIndexHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBVersionAttribute;

import org.sagebionetworks.bridge.models.healthdata.HealthDataAttachment;

/** DynamoDB implementation of {@link org.sagebionetworks.bridge.models.healthdata.HealthDataAttachment}. */
@DynamoThroughput(readCapacity=50, writeCapacity=25)
@DynamoDBTable(tableName = "HealthDataAttachment")
public class DynamoHealthDataAttachment implements HealthDataAttachment {
    private String id;
    private String recordId;
    private Long version;

    /** {@inheritDoc} */
    @DynamoDBHashKey
    @Override
    public String getId() {
        return id;
    }

    /** @see #getId */
    @Override
    public void setId(String id) {
        this.id = id;
    }

    /** {@inheritDoc} */
    @DynamoDBIndexHashKey(attributeName = "recordId", globalSecondaryIndexName = "recordId-index")
    @Override
    public String getRecordId() {
        return recordId;
    }

    /** @see #getRecordId */
    @Override
    public void setRecordId(String recordId) {
        this.recordId = recordId;
    }

    /** {@inheritDoc} */
    @DynamoDBVersionAttribute
    @Override
    public Long getVersion() {
        return version;
    }

    /** @see #getVersion */
    @Override
    public void setVersion(Long version) {
        this.version = version;
    }
}
