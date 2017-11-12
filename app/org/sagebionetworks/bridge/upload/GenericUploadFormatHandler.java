package org.sagebionetworks.bridge.upload;

import java.util.Iterator;
import java.util.Map;
import javax.annotation.Nonnull;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.json.JsonUtils;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolderImpl;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.models.upload.UploadFieldDefinition;
import org.sagebionetworks.bridge.models.upload.UploadFieldType;
import org.sagebionetworks.bridge.models.upload.UploadSchema;
import org.sagebionetworks.bridge.services.SurveyService;
import org.sagebionetworks.bridge.services.UploadSchemaService;

/**
 * <p>
 * Processes uploads for the v2_generic format. This handler reads from
 * {@link org.sagebionetworks.bridge.upload.UploadValidationContext#getUnzippedDataMap} and
 * {@link org.sagebionetworks.bridge.upload.UploadValidationContext#getJsonDataMap} and updates the existing record in
 * {@link org.sagebionetworks.bridge.upload.UploadValidationContext#getHealthDataRecord} and the attachment map in
 * {@link org.sagebionetworks.bridge.upload.UploadValidationContext#getAttachmentsByFieldName}.
 * </p>
 * <p>
 * There is some overlap between this code and IosSchemaValidationHandler. However, we have opted to build this handler
 * as an entirely separate class rather than create a monolithic handler to do both. This is to isolate the v1_legacy
 * stuff from the v2_generic stuff, and to avoid propagating the hacks that were created to address launch day data.
 * Truly shared code has been refactored either into InitRecordHandler and UploadUtil.
 * </p>
 * <p>
 * Name note: The upload format is generic, not the handler.
 * </p>
 */
@Component
public class GenericUploadFormatHandler implements UploadValidationHandler {

    private SurveyService surveyService;
    private UploadSchemaService uploadSchemaService;

    /** Survey service, to get the survey if this upload is a survey. Configured by Spring. */
    @Autowired
    public final void setSurveyService(SurveyService surveyService) {
        this.surveyService = surveyService;
    }

    /** Upload Schema Service, used to get the schema corresponding to the upload. This is configured by Spring. */
    @Autowired
    public final void setUploadSchemaService(UploadSchemaService uploadSchemaService) {
        this.uploadSchemaService = uploadSchemaService;
    }

    /** {@inheritDoc} */
    @Override
    public void handle(@Nonnull UploadValidationContext context) throws UploadValidationException {
        Map<String, byte[]> attachmentMap = context.getAttachmentsByFieldName();
        Map<String, JsonNode> jsonDataMap = context.getJsonDataMap();
        HealthDataRecord record = context.getHealthDataRecord();
        ObjectNode dataMap = (ObjectNode) record.getData();
        StudyIdentifier studyId = context.getStudy();
        Map<String, byte[]> unzippedDataMap = context.getUnzippedDataMap();

        // Get schema from info.json
        JsonNode infoJson = jsonDataMap.get(UploadUtil.FILENAME_INFO_JSON);
        UploadSchema schema = getUploadSchema(studyId, infoJson);
        record.setSchemaId(schema.getSchemaId());
        record.setSchemaRevision(schema.getRevision());

        // Other parameters from info.json.
        parseCreatedOnToRecord(context, infoJson, record);
        String dataFilename = JsonUtils.asText(infoJson, UploadUtil.FIELD_DATA_FILENAME);

        // Parse data into the health data record, using the schema.
        handleData(context, dataFilename, jsonDataMap, unzippedDataMap, schema, dataMap, attachmentMap);
    }

    // Helper method to get a schema based on inputs from info.json.
    // Package-scoped to facilitate unit tests.
    UploadSchema getUploadSchema(StudyIdentifier studyId, JsonNode infoJson) throws UploadValidationException {
        // Try getting by survey first.
        String surveyGuid = JsonUtils.asText(infoJson, UploadUtil.FIELD_SURVEY_GUID);
        String surveyCreatedOn = JsonUtils.asText(infoJson, UploadUtil.FIELD_SURVEY_CREATED_ON);
        if (StringUtils.isNotBlank(surveyGuid) && StringUtils.isNotBlank(surveyCreatedOn)) {
            // surveyCreatedOn is a timestamp. SurveyService takes long epoch millis. Convert.
            long surveyCreatedOnMillis= DateUtils.convertToMillisFromEpoch(surveyCreatedOn);

            // Get survey. We use the survey identifier as the schema ID and the schema revision. Both of these must be
            // specified.
            Survey survey = surveyService.getSurvey(new GuidCreatedOnVersionHolderImpl(surveyGuid,
                    surveyCreatedOnMillis));
            String surveySchemaId = survey.getIdentifier();
            Integer surveySchemaRev = survey.getSchemaRevision();
            if (StringUtils.isBlank(surveySchemaId) || surveySchemaRev == null) {
                throw new UploadValidationException("Schema not found for survey " + surveyGuid + ":" +
                        surveyCreatedOnMillis);
            }

            // Get the schema with the schema ID and rev.
            return uploadSchemaService.getUploadSchemaByIdAndRev(studyId, surveySchemaId, surveySchemaRev);
        }

        // Fall back to getting by schema.
        String schemaId = JsonUtils.asText(infoJson, UploadUtil.FIELD_ITEM);
        Integer schemaRev = JsonUtils.asInt(infoJson, UploadUtil.FIELD_SCHEMA_REV);
        if (StringUtils.isNotBlank(schemaId) && schemaRev != null) {
            return uploadSchemaService.getUploadSchemaByIdAndRev(studyId, schemaId, schemaRev);
        } else {
            throw new UploadValidationException("info.json must contain either item and schemaRevision or " +
                    "surveyGuid and surveyCreatedOn");
        }
    }

    // Helper method to read and parse the createdOn from info.json and add it to the health data record. Handles
    // fall-back logic.
    // Package-scoped to facilitate unit tests.
    static void parseCreatedOnToRecord(UploadValidationContext context, JsonNode infoJson, HealthDataRecord record) {
        // createdOn string from info.json
        String createdOnString = JsonUtils.asText(infoJson, UploadUtil.FIELD_CREATED_ON);

        // Parse into a Joda DateTime.
        DateTime createdOn = null;
        if (StringUtils.isNotBlank(createdOnString)) {
            try {
                createdOn = DateTime.parse(createdOnString);
            } catch (IllegalArgumentException ex) {
                // Write a message to the validation context, but there's no need to log or throw.
                context.addMessage("Invalid date-time: " + createdOnString);
            }
        }

        if (createdOn != null) {
            // Use createdOn and timezone as specified in the upload.
            record.setCreatedOn(createdOn.getMillis());
            record.setCreatedOnTimeZone(HealthDataRecord.TIME_ZONE_FORMATTER.print(createdOn));
        } else {
            // Fall back to current time. Don't set a timezone, since it's indeterminate.
            context.addMessage("Upload has no createdOn; using current time.");
            record.setCreatedOn(DateUtils.getCurrentMillisFromEpoch());
        }
    }

    // Helper method that, copies health data from the jsonDataMap and unzippedData maps to the dataMap or
    // attachmentMap, based on a schema. Also handles flattening and sanitization.
    private static void handleData(UploadValidationContext context, String dataFilename,
            Map<String, JsonNode> jsonDataMap, Map<String, byte[]> unzippedDataMap, UploadSchema schema,
            ObjectNode dataMap, Map<String, byte[]> attachmentMap) {
        JsonNode dataFileNode = null;
        if (StringUtils.isNotBlank(dataFilename)) {
            dataFileNode = jsonDataMap.get(dataFilename);
        }

        // Get flattened JSON data map (key is filename.fieldname), because schemas can reference fields either by
        // filename.fieldname or wholly by filename.
        // Note that this includes both the flattened map (filename.fieldname) and the whole file (filename).
        Map<String, JsonNode> flattenedJsonDataMap = UploadUtil.flattenJsonDataMap(jsonDataMap);

        // Add the fields from the file specified in dataFilename as top-level keys in the flattened map.
        if (dataFileNode != null) {
            Iterator<String> fieldNameIter = dataFileNode.fieldNames();
            while (fieldNameIter.hasNext()) {
                String oneFieldName = fieldNameIter.next();
                flattenedJsonDataMap.put(oneFieldName, dataFileNode.get(oneFieldName));
            }
        }

        // Sanitize field names for both JSON and non-JSON.
        Map<String, JsonNode> sanitizedFlattenedJsonDataMap = UploadUtil.sanitizeFieldNames(flattenedJsonDataMap);
        Map<String, byte[]> sanitizedUnzippedDataMap = UploadUtil.sanitizeFieldNames(unzippedDataMap);

        // Using schema, copy fields over to data map. Or if it's an attachment, add it to the attachment map.
        for (UploadFieldDefinition oneFieldDef : schema.getFieldDefinitions()) {
            String fieldName = oneFieldDef.getName();

            if (sanitizedUnzippedDataMap.containsKey(fieldName)) {
                UploadUtil.addAttachment(attachmentMap, fieldName, sanitizedUnzippedDataMap.get(fieldName));
            } else if (sanitizedFlattenedJsonDataMap.containsKey(fieldName)) {
                copyJsonField(context, sanitizedFlattenedJsonDataMap.get(fieldName), oneFieldDef, dataMap,
                        attachmentMap);
            }
        }
    }

    // Helper method to copy a JSON field value to the data or attachment map.
    private static void copyJsonField(UploadValidationContext context, JsonNode fieldValue,
            UploadFieldDefinition fieldDef, ObjectNode dataMap, Map<String, byte[]> attachmentMap) {
        String fieldName = fieldDef.getName();

        // Skip nulls.
        if (fieldValue == null || fieldValue.isNull()) {
            context.addMessage("Field " + fieldName + " is null.");
            return;
        }

        // Attachment map or data map, based on field type.
        if (UploadFieldType.ATTACHMENT_TYPE_SET.contains(fieldDef.getType())) {
            try {
                UploadUtil.addAttachment(attachmentMap, fieldName, BridgeObjectMapper.get().writeValueAsBytes(
                        fieldValue));
            } catch (JsonProcessingException ex) {
                context.addMessage("Field " + fieldName + " could not be converted from JSON: " + ex.getMessage());
            }
        } else {
            dataMap.set(fieldName, fieldValue);
        }
    }
}