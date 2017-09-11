package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableList;
import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.joda.time.LocalDate;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;
import org.sagebionetworks.bridge.models.healthdata.HealthDataSubmission;
import org.sagebionetworks.bridge.models.upload.UploadFieldDefinition;
import org.sagebionetworks.bridge.models.upload.UploadFieldType;
import org.sagebionetworks.bridge.models.upload.UploadSchema;
import org.sagebionetworks.bridge.upload.StrictValidationHandler;
import org.sagebionetworks.bridge.upload.TranscribeConsentHandler;
import org.sagebionetworks.bridge.upload.UploadArtifactsHandler;
import org.sagebionetworks.bridge.upload.UploadUtil;
import org.sagebionetworks.bridge.upload.UploadValidationContext;
import org.sagebionetworks.bridge.upload.UploadValidationException;

public class HealthDataServiceSubmitHealthDataTest {
    private static final String APP_VERSION = "version 1.0.0, build 2";
    private static final JsonNode DATA = BridgeObjectMapper.get().createObjectNode();
    private static final String HEALTH_CODE = "test-health-code";
    private static final String PHONE_INFO = "Unit Tests";
    private static final String RECORD_ID = "test-record";
    private static final String SCHEMA_ID = "test-schema";
    private static final int SCHEMA_REV = 3;

    private static final DateTime CREATED_ON = DateTime.parse("2017-08-24T14:38:57.340+0900");
    private static final long CREATED_ON_MILLIS = CREATED_ON.getMillis();
    private static final String CREATED_ON_TIMEZONE = "+0900";

    private static final LocalDate MOCK_NOW_DATE = LocalDate.parse("2017-05-19");
    private static final long MOCK_NOW_MILLIS = DateTime.parse("2017-05-19T14:45:27.593-0700").getMillis();

    private static final StudyParticipant PARTICIPANT = new StudyParticipant.Builder().withHealthCode(HEALTH_CODE)
            .build();

    @BeforeClass
    public static void mockNow() {
        DateTimeUtils.setCurrentMillisFixed(MOCK_NOW_MILLIS);
    }

    @AfterClass
    public static void unmockNow() {
        DateTimeUtils.setCurrentMillisSystem();
    }

    @Test(expected = InvalidEntityException.class)
    public void nullSubmission() throws Exception {
        new HealthDataService().submitHealthData(TestConstants.TEST_STUDY, PARTICIPANT, null);
    }

    @Test(expected = InvalidEntityException.class)
    public void invalidSubmission() throws Exception {
        HealthDataSubmission submission = makeValidBuilder().withData(null).build();
        new HealthDataService().submitHealthData(TestConstants.TEST_STUDY, PARTICIPANT, submission);
    }

    @Test
    public void submitHealthData() throws Exception {
        // mock schema service
        List<UploadFieldDefinition> fieldDefList = ImmutableList.of(
                new UploadFieldDefinition.Builder().withName("sanitize____this").withType(UploadFieldType.STRING)
                        .build(),
                new UploadFieldDefinition.Builder().withName("no-value-field").withType(UploadFieldType.STRING)
                        .withRequired(false).build(),
                new UploadFieldDefinition.Builder().withName("null-value-field").withType(UploadFieldType.STRING)
                        .withRequired(false).build(),
                new UploadFieldDefinition.Builder().withName("attachment-field")
                        .withType(UploadFieldType.ATTACHMENT_V2).build(),
                new UploadFieldDefinition.Builder().withName("normal-field").withType(UploadFieldType.STRING).build());

        UploadSchema schema = UploadSchema.create();
        schema.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);
        schema.setSchemaId(SCHEMA_ID);
        schema.setRevision(SCHEMA_REV);
        schema.setFieldDefinitions(fieldDefList);

        UploadSchemaService mockSchemaService = mock(UploadSchemaService.class);
        when(mockSchemaService.getUploadSchemaByIdAndRev(TestConstants.TEST_STUDY, SCHEMA_ID, SCHEMA_REV)).thenReturn(
                schema);

        // mock handlers
        StrictValidationHandler mockStrictValidationHandler = mock(StrictValidationHandler.class);
        TranscribeConsentHandler mockTranscribeConsentHandler = mock(TranscribeConsentHandler.class);
        UploadArtifactsHandler mockUploadArtifactsHandler = mock(UploadArtifactsHandler.class);

        // UploadArtifactsHandler needs to write record ID back into the context.
        doAnswer(invocation -> {
            UploadValidationContext context = invocation.getArgumentAt(0, UploadValidationContext.class);
            context.setRecordId(RECORD_ID);
            return null;
        }).when(mockUploadArtifactsHandler).handle(any());

        // set up service
        HealthDataService svc = spy(new HealthDataService());
        svc.setSchemaService(mockSchemaService);
        svc.setStrictValidationHandler(mockStrictValidationHandler);
        svc.setTranscribeConsentHandler(mockTranscribeConsentHandler);
        svc.setUploadArtifactsHandler(mockUploadArtifactsHandler);

        // Spy getRecordById(). This decouples the submitHealthData implementation from the getRecord implementation.
        // At this point, we only care about data flow. Don't worry about the actual content.
        HealthDataRecord internalRecord = HealthDataRecord.create();
        doReturn(internalRecord).when(svc).getRecordById(RECORD_ID);

        // setup input
        ObjectNode inputData = BridgeObjectMapper.get().createObjectNode();
        inputData.put("sanitize!@#$this", "sanitize this value");
        inputData.putNull("null-value-field");
        inputData.put("attachment-field", "attachment field value");
        inputData.put("normal-field", "normal field value");
        inputData.put("non-schema-field", "this is not in the schema");

        HealthDataSubmission submission = makeValidBuilder().withData(inputData).build();

        // execute
        HealthDataRecord svcOutputRecord = svc.submitHealthData(TestConstants.TEST_STUDY, PARTICIPANT, submission);

        // verify that we return the record returned by the internal getRecordById() call.
        assertSame(internalRecord, svcOutputRecord);

        // Verify strict validation handler called. While we're at it, verify that we constructed the context and
        // record correctly.
        ArgumentCaptor<UploadValidationContext> contextCaptor = ArgumentCaptor.forClass(UploadValidationContext.class);
        verify(mockStrictValidationHandler).handle(contextCaptor.capture());

        UploadValidationContext context = contextCaptor.getValue();
        assertEquals(HEALTH_CODE, context.getHealthCode());
        assertEquals(TestConstants.TEST_STUDY, context.getStudy());

        // We have one attachment. Note: This includes the quote marks, because we serialize the entire JSON field.
        // (This will normally be arrays or objects.)
        Map<String, byte[]> attachmentMap = context.getAttachmentsByFieldName();
        assertEquals(1, attachmentMap.size());
        assertEquals("\"attachment field value\"", new String(attachmentMap.get("attachment-field")));

        // validate the created record
        HealthDataRecord contextRecord = context.getHealthDataRecord();
        assertEquals(APP_VERSION, contextRecord.getAppVersion());
        assertEquals(PHONE_INFO, contextRecord.getPhoneInfo());
        assertEquals(SCHEMA_ID, contextRecord.getSchemaId());
        assertEquals(SCHEMA_REV, contextRecord.getSchemaRevision());
        assertEquals(HEALTH_CODE, contextRecord.getHealthCode());
        assertEquals(TestConstants.TEST_STUDY_IDENTIFIER, contextRecord.getStudyId());
        assertEquals(MOCK_NOW_DATE, contextRecord.getUploadDate());
        assertEquals(MOCK_NOW_MILLIS, contextRecord.getUploadedOn().longValue());
        assertEquals(CREATED_ON_MILLIS, contextRecord.getCreatedOn().longValue());
        assertEquals(CREATED_ON_TIMEZONE, contextRecord.getCreatedOnTimeZone());

        // validate the sanitized, filtered data
        JsonNode filteredData = contextRecord.getData();
        assertEquals(2, filteredData.size());
        assertEquals("sanitize this value", filteredData.get("sanitize____this").textValue());
        assertEquals("normal field value", filteredData.get("normal-field").textValue());

        // validate app version and phone info in metadata
        JsonNode metadata = contextRecord.getMetadata();
        assertEquals(2, metadata.size());
        assertEquals(APP_VERSION, metadata.get(UploadUtil.FIELD_APP_VERSION).textValue());
        assertEquals(PHONE_INFO, metadata.get(UploadUtil.FIELD_PHONE_INFO).textValue());

        // validate the other handlers are called
        verify(mockTranscribeConsentHandler).handle(context);
        verify(mockUploadArtifactsHandler).handle(context);
    }

    @Test(expected = BadRequestException.class)
    public void strictValidationThrows() throws Exception {
        // mock schema service
        List<UploadFieldDefinition> fieldDefList = ImmutableList.of(new UploadFieldDefinition.Builder()
                .withName("simple-field").withType(UploadFieldType.INT).build());

        UploadSchema schema = UploadSchema.create();
        schema.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);
        schema.setSchemaId(SCHEMA_ID);
        schema.setRevision(SCHEMA_REV);
        schema.setFieldDefinitions(fieldDefList);

        UploadSchemaService mockSchemaService = mock(UploadSchemaService.class);
        when(mockSchemaService.getUploadSchemaByIdAndRev(TestConstants.TEST_STUDY, SCHEMA_ID, SCHEMA_REV)).thenReturn(
                schema);

        // mock handlers - Only StrictValidationHandler will be called. Also, since we're not calling the actual
        // StrictValidationHandler, we need to make it throw.
        StrictValidationHandler mockStrictValidationHandler = mock(StrictValidationHandler.class);
        doThrow(UploadValidationException.class).when(mockStrictValidationHandler).handle(any());

        // set up service
        HealthDataService svc = new HealthDataService();
        svc.setSchemaService(mockSchemaService);
        svc.setStrictValidationHandler(mockStrictValidationHandler);

        // setup input
        ObjectNode inputData = BridgeObjectMapper.get().createObjectNode();
        inputData.put("simple-field", "not an int");
        HealthDataSubmission submission = makeValidBuilder().withData(inputData).build();

        // execute - This throws.
        svc.submitHealthData(TestConstants.TEST_STUDY, PARTICIPANT, submission);
    }

    private static HealthDataSubmission.Builder makeValidBuilder() {
        return new HealthDataSubmission.Builder().withAppVersion(APP_VERSION).withCreatedOn(CREATED_ON).withData(DATA)
                .withPhoneInfo(PHONE_INFO).withSchemaId(SCHEMA_ID).withSchemaRevision(SCHEMA_REV);
    }
}