package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import javax.annotation.Resource;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.dao.ConsentAlreadyExistsException;
import org.sagebionetworks.bridge.dao.ConsentNotFoundException;
import org.sagebionetworks.bridge.models.ConsentSignature;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class DynamoUserConsentDaoTest {

    @Resource
    private DynamoUserConsentDao userConsentDao;

    @Before
    public void before() {
        DynamoInitializer.init("org.sagebionetworks.bridge.dynamodb");
        DynamoTestUtil.clearTable(DynamoUserConsent.class, "name", "birthdate", "give", "studyKey", "consentTimestamp",
                "version");
        DynamoTestUtil.clearTable(DynamoUserConsent2.class, "signedOn", "dataSharing", "name", "birthdate", "studyKey",
                "healthCode", "consentCreatedOn", "version");
    }

    @After
    public void after() {
        DynamoTestUtil.clearTable(DynamoUserConsent.class, "name", "birthdate", "give", "studyKey", "consentTimestamp",
                "version");
        DynamoTestUtil.clearTable(DynamoUserConsent2.class, "signedOn", "dataSharing", "name", "birthdate", "studyKey",
                "healthCode", "consentCreatedOn", "version");
    }

    @Test
    public void test() {

        // Not consented yet
        final String healthCode = "hc789";
        final DynamoStudyConsent1 consent = new DynamoStudyConsent1();
        consent.setStudyKey("study123");
        consent.setCreatedOn(123L);
        assertFalse(userConsentDao.hasConsented(healthCode, consent));
        assertFalse(userConsentDao.hasConsentedNew(healthCode, consent));
        assertNull(userConsentDao.getConsentCreatedOn(healthCode, consent.getStudyKey()));

        // Give consent
        final ConsentSignature consentSignature = new ConsentSignature("John Smith", "1999-12-01");
        userConsentDao.giveConsent(healthCode, consent, consentSignature);
        assertTrue(userConsentDao.hasConsented(healthCode, consent));
        assertTrue(userConsentDao.hasConsentedNew(healthCode, consent));
        assertEquals(Long.valueOf(123), userConsentDao.getConsentCreatedOn(healthCode, consent.getStudyKey()));
        ConsentSignature cs = userConsentDao.getConsentSignature(healthCode, consent);
        assertEquals(consentSignature.getName(), cs.getName());

        // Cannot give consent again if already consented
        try {
            userConsentDao.giveConsent(healthCode, consent, consentSignature);
        } catch (ConsentAlreadyExistsException e) {
            assertTrue(true); // Expected
        }
        try {
            userConsentDao.giveConsentNew(healthCode, consent, consentSignature);
        } catch (ConsentAlreadyExistsException e) {
            assertTrue(true); // Expected
        }

        // Withdraw
        userConsentDao.withdrawConsent(healthCode, consent);
        assertFalse(userConsentDao.hasConsented(healthCode, consent));
        assertFalse(userConsentDao.hasConsentedNew(healthCode, consent));

        // Can give consent again if the previous consent is withdrawn
        userConsentDao.giveConsent(healthCode, consent, consentSignature);
        assertTrue(userConsentDao.hasConsented(healthCode, consent));
        assertTrue(userConsentDao.hasConsentedNew(healthCode, consent));

        // Withdraw again
        userConsentDao.withdrawConsent(healthCode, consent);
        assertFalse(userConsentDao.hasConsented(healthCode, consent));
        assertFalse(userConsentDao.hasConsentedNew(healthCode, consent));

    }

    @Test
    public void testDataSharing() {

        // Not consented yet
        final String healthCode = "hc123";
        final DynamoStudyConsent1 consent = new DynamoStudyConsent1();
        consent.setStudyKey("study789");
        consent.setCreatedOn(456L);
        assertFalse(userConsentDao.hasConsented(healthCode, consent));

        // Must consent first
        assertFalse(userConsentDao.isSharingData(healthCode, consent));
        try {
            userConsentDao.getConsentSignature(healthCode, consent);
        } catch (ConsentNotFoundException e) {
            assertTrue(true); // Expected
        }
        try {
            userConsentDao.withdrawConsent(healthCode, consent);
        } catch (ConsentNotFoundException e) {
            assertTrue(true); // Expected
        }

        // Must consent first before sharing data
        assertFalse(userConsentDao.isSharingData(healthCode, consent));
        try {
            userConsentDao.resumeSharing(healthCode, consent);
        } catch (ConsentNotFoundException e) {
            assertTrue(true); // Expected
        }
        try {
            userConsentDao.suspendSharing(healthCode, consent);
        } catch (ConsentNotFoundException e) {
            assertTrue(true); // Expected
        }

        // Give consent
        final ConsentSignature consentSignature = new ConsentSignature("John Smith", "2009-12-01");
        userConsentDao.giveConsent(healthCode, consent, consentSignature);
        assertTrue(userConsentDao.hasConsented(healthCode, consent));
        assertTrue(userConsentDao.isSharingData(healthCode, consent));

        // Share data
        userConsentDao.resumeSharing(healthCode, consent);
        assertTrue(userConsentDao.isSharingData(healthCode, consent));
        userConsentDao.suspendSharing(healthCode, consent);
        assertFalse(userConsentDao.isSharingData(healthCode, consent));
        userConsentDao.resumeSharing(healthCode, consent);
        assertTrue(userConsentDao.isSharingData(healthCode, consent));
    }

    @Test
    public void testBackfill() {
        final String healthCode = "hc123";
        final DynamoStudyConsent1 consent = new DynamoStudyConsent1();
        consent.setStudyKey("study789");
        consent.setCreatedOn(456L);
        ConsentSignature researchConsent = new ConsentSignature("John Smith", "2009-12-01");
        assertFalse(userConsentDao.hasConsented(healthCode, consent));
        userConsentDao.giveConsentOld(healthCode, consent, researchConsent);
        assertFalse(userConsentDao.hasConsentedNew(healthCode, consent));
        assertEquals(1, userConsentDao.backfill());
        assertTrue(userConsentDao.hasConsentedNew(healthCode, consent));
        assertTrue(userConsentDao.getConsentCreatedOnNew(healthCode, consent.getStudyKey()) > 0L);
        ConsentSignature signature = userConsentDao.getConsentSignatureNew(healthCode, consent);
        assertEquals("John Smith", signature.getName());
        assertEquals("2009-12-01", signature.getBirthdate());
    }
}