package org.sagebionetworks.bridge.dynamodb;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.sagebionetworks.bridge.BridgeConstants.API_MAXIMUM_PAGE_SIZE;
import static com.amazonaws.services.dynamodbv2.model.ComparisonOperator.BEGINS_WITH;
import static com.amazonaws.services.dynamodbv2.model.ComparisonOperator.NOT_NULL;
import static com.amazonaws.services.dynamodbv2.model.ComparisonOperator.NULL;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.annotation.Resource;

import com.amazonaws.services.dynamodbv2.datamodeling.QueryResultPage;
import com.amazonaws.services.dynamodbv2.model.ReturnConsumedCapacity;
import com.google.common.util.concurrent.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.config.Config;
import org.sagebionetworks.bridge.dao.ExternalIdDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifier;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifierInfo;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper.FailedBatch;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBSaveExpression;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import com.amazonaws.services.dynamodbv2.model.ExpectedAttributeValue;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

@Component
public class DynamoExternalIdDao implements ExternalIdDao {
    
    static final String PAGE_SIZE_ERROR = "pageSize must be from 1-"+API_MAXIMUM_PAGE_SIZE+" records";
    static final int PAGE_SCAN_LIMIT = 200;

    private static final Logger LOG = LoggerFactory.getLogger(DynamoExternalIdDao.class);

    private static final String HEALTH_CODE = "healthCode";
    static final String IDENTIFIER = "identifier";
    private static final String STUDY_ID = "studyId";

    private int addLimit;
    private RateLimiter getExternalIdRateLimiter;
    private DynamoDBMapper mapper;

    /** Gets the add limit and lock duration from Config. */
    @Autowired
    public final void setConfig(Config config) {
        addLimit = config.getInt(CONFIG_KEY_ADD_LIMIT);
        setGetExternalIdRateLimiter(RateLimiter.create(config.getInt(EXTERNAL_ID_GET_RATE)));
    }

    // allow unit test to mock this
    void setGetExternalIdRateLimiter(RateLimiter getExternalIdRateLimiter) {
        this.getExternalIdRateLimiter = getExternalIdRateLimiter;
    }

    @Resource(name = "externalIdDdbMapper")
    public final void setMapper(DynamoDBMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public ExternalIdentifier getExternalId(StudyIdentifier studyId, String externalId) {
        checkNotNull(studyId);
        checkNotNull(externalId);
        
        DynamoExternalIdentifier key = new DynamoExternalIdentifier(studyId, externalId);
        return mapper.load(key);
    }
    
    @Override
    public ForwardCursorPagedResourceList<ExternalIdentifierInfo> getExternalIds(StudyIdentifier studyId,
            String offsetKey, final int pageSize, String idFilter, Boolean assignmentFilter) {
        checkNotNull(studyId);

        // Just set a sane upper limit on this.
        // pageSize is used here to determine limit the number of results to be returned, as well as amount of records
        // to scan per call to dynamo
        if (pageSize < 1 || pageSize > API_MAXIMUM_PAGE_SIZE) {
            throw new BadRequestException(PAGE_SIZE_ERROR);
        }

        LOG.debug("Page Size: " + pageSize);

        // The offset key is applied after the idFilter. If the offsetKey doesn't match the beginning
        // of the idFilter, the AWS SDK throws a validation exception. So when providing an idFilter and 
        // a paging offset, clear the offset (go back to the first page) if they don't match.
        if (offsetKey != null && idFilter != null && !offsetKey.startsWith(idFilter)) {
            offsetKey = null;
        }
        String nextPageOffsetKey = offsetKey;
        
        QueryResultPage<DynamoExternalIdentifier> list;
        List<ExternalIdentifierInfo> identifiers = Lists.newArrayListWithCapacity(pageSize);

        // initial estimate: read capacity consumed will equal 1
        // see https://aws.amazon.com/blogs/developer/rate-limited-scans-in-amazon-dynamodb/
        int capacityAcquired = 1;
        int capacityConsumed = 0;

        do {
            getExternalIdRateLimiter.acquire(capacityAcquired);

            list = mapper.queryPage(DynamoExternalIdentifier.class,
                    createGetQuery(studyId, nextPageOffsetKey, PAGE_SCAN_LIMIT, idFilter, assignmentFilter));
            for (ExternalIdentifier id : list.getResults()) {
                if (identifiers.size() == pageSize) {
                    // return no more than pageSize externalIdentifiers
                    break;
                }
                identifiers.add(new ExternalIdentifierInfo(id.getIdentifier(), id.getHealthCode() != null));
            }

            capacityConsumed = list.getConsumedCapacity().getCapacityUnits().intValue();
            LOG.debug("Capacity acquired: " + capacityAcquired + ", Consumed Capacity: " + capacityConsumed);

            // use capacity consumed by last request to as our estimate for the next request
            capacityAcquired = capacityConsumed;

            if (list.getCount() > pageSize) {
                // we retrieved more records from Dynamo than we are returning
                nextPageOffsetKey = identifiers.get(pageSize - 1).getIdentifier();
            } else {
                // This is the last key, not the next key of the next page of records. It only exists if there's a record
                // beyond the records we've converted to a page. Then get the last key in the list.
                Map<String, AttributeValue> lastEvaluated = list.getLastEvaluatedKey();
                nextPageOffsetKey = lastEvaluated != null ? lastEvaluated.get(IDENTIFIER).getS() : null;
            }
        } while ((identifiers.size() < pageSize) && (nextPageOffsetKey != null));

        return new ForwardCursorPagedResourceList<>(
                identifiers, nextPageOffsetKey)
                .withRequestParam(ResourceList.OFFSET_KEY, offsetKey)
                .withRequestParam(ResourceList.PAGE_SIZE, pageSize)
                .withRequestParam(ResourceList.ID_FILTER, idFilter)
                .withRequestParam(ResourceList.ASSIGNMENT_FILTER, assignmentFilter);
    }

    @Override
    public void addExternalIds(StudyIdentifier studyId, List<String> externalIds) {
        checkNotNull(studyId);
        checkNotNull(externalIds);

        // We validate a wider range of issues in the service, but check size again because this is 
        // specifically a database capacity issue.
        if (externalIds.size() > addLimit) {
            throw new BadRequestException("List of externalIds is too large; size=" + externalIds.size() + ", limit=" + addLimit);
        }
        List<DynamoExternalIdentifier> idsToSave = externalIds.stream().map(id -> {
            return new DynamoExternalIdentifier(studyId, id);
        }).filter(externalId -> {
            return mapper.load(externalId) == null;
        }).collect(Collectors.toList());
        
        if (!idsToSave.isEmpty()) {
            List<FailedBatch> failures = mapper.batchSave(idsToSave);
            BridgeUtils.ifFailuresThrowException(failures);
        }
    }
    
    @Override
    public void assignExternalId(StudyIdentifier studyId, String externalId, String healthCode) {
        checkNotNull(studyId);
        checkArgument(isNotBlank(externalId));
        checkArgument(isNotBlank(healthCode));
        
        DynamoExternalIdentifier keyObject = new DynamoExternalIdentifier(studyId, externalId);
        DynamoExternalIdentifier identifier = mapper.load(keyObject);

        // If the identifier doesn't exist, or the same code has already been set, do nothing
        if (identifier != null && !healthCode.equals(identifier.getHealthCode())) {
            try {
                identifier.setHealthCode(healthCode);
                mapper.save(identifier, getAssignmentExpression());
            } catch(ConditionalCheckFailedException e) {
                // If this happens, it's a consistency error because the account should have failed. We need to reconcile.
                LOG.error("Failed attempt to assign externalId: " + externalId + " from " + identifier.getHealthCode()
                        + " to " + healthCode);
                throw new EntityAlreadyExistsException(ExternalIdentifier.class, "identifier", identifier.getIdentifier());
            }        
        }
    }

    @Override
    public void unassignExternalId(StudyIdentifier studyId, String externalId) {
        checkNotNull(studyId);
        checkArgument(isNotBlank(externalId));
        
        DynamoExternalIdentifier keyObject = new DynamoExternalIdentifier(studyId, externalId);
        
        // Don't throw an exception if the identifier doesn't exist, we don't care.
        DynamoExternalIdentifier identifier = mapper.load(keyObject);
        if (identifier != null) {
            identifier.setHealthCode(null);
            mapper.save(identifier);
        }
    }
    
    /**
     * This is intended for testing. Deleting a large number of identifiers will cause DynamoDB capacity exceptions.
     */
    @Override
    public void deleteExternalIds(StudyIdentifier studyId, List<String> externalIds) {
        checkNotNull(studyId);
        checkNotNull(externalIds);
        
        if (!externalIds.isEmpty()) {
            List<DynamoExternalIdentifier> idsToDelete = externalIds.stream().map(id -> {
                return new DynamoExternalIdentifier(studyId, id);
            }).collect(Collectors.toList());
            
            List<FailedBatch> failures = mapper.batchDelete(idsToDelete);
            BridgeUtils.ifFailuresThrowException(failures);
        }
    }

    /**
     * Get the count query (applies filters) and then sets an offset key and the limit to a page of records, 
     * plus one, to determine if there are records beyond the current page. 
     */
    private DynamoDBQueryExpression<DynamoExternalIdentifier> createGetQuery(StudyIdentifier studyId,
            String offsetKey, int pageSize, String idFilter, Boolean assignmentFilter) {

        DynamoDBQueryExpression<DynamoExternalIdentifier> query =
                new DynamoDBQueryExpression<DynamoExternalIdentifier>();
        if (idFilter != null) {
            query.withRangeKeyCondition(IDENTIFIER, new Condition()
                    .withAttributeValueList(new AttributeValue().withS(idFilter))
                    .withComparisonOperator(BEGINS_WITH));
        }
        if (assignmentFilter != null) {
            addAssignmentFilter(query, assignmentFilter.booleanValue());
        }
        query.withHashKeyValues(new DynamoExternalIdentifier(studyId, null)); // no healthCode.

        if (offsetKey != null) {
            Map<String, AttributeValue> map = new HashMap<>();
            map.put(STUDY_ID, new AttributeValue().withS(studyId.getIdentifier()));
            map.put(IDENTIFIER, new AttributeValue().withS(offsetKey));
            query.withExclusiveStartKey(map);
        }

        query.withReturnConsumedCapacity(ReturnConsumedCapacity.TOTAL);
        query.withConsistentRead(true);
        query.withLimit(pageSize);
        return query;
    }

    private void addAssignmentFilter(DynamoDBQueryExpression<DynamoExternalIdentifier> query, boolean isAssigned) {
        ComparisonOperator healthCodeOp = (isAssigned) ? NOT_NULL : NULL;
        
        Condition healthCodeCondition = new Condition().withComparisonOperator(healthCodeOp);
        query.withQueryFilterEntry(HEALTH_CODE, healthCodeCondition);
    }
    
    /**
     * Save the record with the user's healthCode.  
     */
    private DynamoDBSaveExpression getAssignmentExpression() {
        Map<String, ExpectedAttributeValue> map = Maps.newHashMap();
        map.put(HEALTH_CODE, new ExpectedAttributeValue().withExists(false));

        DynamoDBSaveExpression saveExpression = new DynamoDBSaveExpression();
        saveExpression.setExpected(map);
        return saveExpression;
    }
    
}
