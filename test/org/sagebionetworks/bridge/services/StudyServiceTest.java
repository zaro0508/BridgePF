package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

import javax.annotation.Resource;

import com.google.common.collect.Sets;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.client.exceptions.SynapseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.dao.SubpopulationDao;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.models.studies.EmailTemplate;
import org.sagebionetworks.bridge.models.studies.MimeType;
import org.sagebionetworks.bridge.models.studies.PasswordPolicy;
import org.sagebionetworks.bridge.models.studies.SmsTemplate;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.subpopulations.StudyConsentView;
import org.sagebionetworks.bridge.models.subpopulations.Subpopulation;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.models.upload.UploadValidationStrictness;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class StudyServiceTest {

    @Resource
    StudyService studyService;
    
    @Resource
    StudyConsentService studyConsentService;
    
    @Resource
    SubpopulationService subpopService;

    @Resource
    SubpopulationDao subpopDao;

    @Autowired
    CacheProvider cache;
    
    private CacheProvider mockCache;
    
    private Study study;

    @Before
    public void before() throws SynapseException {
        mockCache = mock(CacheProvider.class);
        studyService.setCacheProvider(mockCache);
    }
    
    @After
    public void after() throws SynapseException {
        if (study != null) {
            studyService.deleteStudy(study.getIdentifier(), true);
        }
    }

    @After
    public void resetCache() {
        studyService.setCacheProvider(cache);
    }

    @Test(expected=InvalidEntityException.class)
    public void studyIsValidated() {
        Study testStudy = new DynamoStudy();
        testStudy.setName("Belgian Waffles [Test]");
        studyService.createStudy(testStudy);
    }
    
    @Test
    public void cannotCreateAnExistingStudyWithAVersion() {
        study = TestUtils.getValidStudy(StudyServiceTest.class);
        study = studyService.createStudy(study);
        try {
            study = studyService.createStudy(study);
            fail("Should have thrown an exception");
        } catch(EntityAlreadyExistsException e) {
            // expected exception
        }
    }
    
    @Test(expected=EntityAlreadyExistsException.class)
    public void cannotCreateAStudyWithAVersion() {
        Study testStudy = TestUtils.getValidStudy(StudyServiceTest.class);
        testStudy.setVersion(1L);
        studyService.createStudy(testStudy);
    }
    
    @Test
    public void crudStudy() {
        study = TestUtils.getValidStudy(StudyServiceTest.class);
        // verify this can be null, that's okay, and the flags are reset correctly on create
        study.setConsentNotificationEmailVerified(true);
        study.setStudyIdExcludedInExport(false);
        study.setTaskIdentifiers(null);
        study.setUploadValidationStrictness(null);
        study.setActivityEventKeys(null);
        study.setHealthCodeExportEnabled(true);
        study.setActive(false);
        study.setStrictUploadValidationEnabled(false);
        study.setEmailVerificationEnabled(false);
        study.setEmailSignInEnabled(true);
        study.setPhoneSignInEnabled(true);
        study = studyService.createStudy(study);

        // Verify that the flags are set correctly on create.
        assertFalse(study.isConsentNotificationEmailVerified());
        assertNotNull("Version has been set", study.getVersion());
        assertTrue(study.isActive());
        assertFalse(study.isStrictUploadValidationEnabled());
        assertTrue(study.isStudyIdExcludedInExport());
        assertEquals(UploadValidationStrictness.REPORT, study.getUploadValidationStrictness());

        verify(mockCache).setStudy(study);
        reset(mockCache);
        
        // A default, active consent should be created for the study.
        Subpopulation subpop = subpopService.getSubpopulation(study.getStudyIdentifier(),
                SubpopulationGuid.create(study.getIdentifier()));
        StudyConsentView view = studyConsentService.getActiveConsent(subpop);
        assertTrue(view.getDocumentContent().contains("This is a placeholder for your consent document."));

        Study newStudy = studyService.getStudy(study.getIdentifier());
        assertTrue(newStudy.isActive());
        assertFalse(newStudy.isStrictUploadValidationEnabled());
        assertTrue(newStudy.isStudyIdExcludedInExport());
        assertEquals(UploadValidationStrictness.REPORT, newStudy.getUploadValidationStrictness());

        // Verify that the missing templates where created
        assertNotNull(newStudy.getEmailSignInTemplate());
        assertNotNull(newStudy.getAccountExistsTemplate());
        assertNotNull(newStudy.getResetPasswordSmsTemplate());
        assertNotNull(newStudy.getPhoneSignInSmsTemplate());
        assertNotNull(newStudy.getAppInstallLinkSmsTemplate());
        assertNotNull(newStudy.getVerifyPhoneSmsTemplate());
        assertNotNull(newStudy.getAccountExistsSmsTemplate());
        
        assertEquals(study.getIdentifier(), newStudy.getIdentifier());
        assertEquals("Test Study [StudyServiceTest]", newStudy.getName());
        assertEquals(18, newStudy.getMinAgeOfConsent());
        assertEquals(Sets.newHashSet("beta_users", "production_users", BridgeConstants.TEST_USER_GROUP),
                newStudy.getDataGroups());
        assertEquals(0, newStudy.getTaskIdentifiers().size());
        assertEquals(0, newStudy.getActivityEventKeys().size());

        // these should have been changed
        assertEquals("${studyName} link", newStudy.getEmailSignInTemplate().getSubject());
        assertEquals("Follow link ${url}", newStudy.getEmailSignInTemplate().getBody());
        
        verify(mockCache).getStudy(newStudy.getIdentifier());
        verify(mockCache).setStudy(newStudy);
        reset(mockCache);

        // make some (non-admin) updates
        newStudy.setConsentNotificationEmailVerified(true);
        newStudy.setStrictUploadValidationEnabled(true);
        newStudy.setUploadValidationStrictness(UploadValidationStrictness.WARNING);
        Study updatedStudy = studyService.updateStudy(newStudy, false);
        assertFalse(updatedStudy.isConsentNotificationEmailVerified());
        assertTrue(updatedStudy.isStrictUploadValidationEnabled());
        assertEquals(UploadValidationStrictness.WARNING, updatedStudy.getUploadValidationStrictness());

        verify(mockCache).removeStudy(updatedStudy.getIdentifier());
        verify(mockCache).setStudy(updatedStudy);
        reset(mockCache);

        // delete study
        studyService.deleteStudy(study.getIdentifier(), true);
        verify(mockCache).getStudy(study.getIdentifier());
        verify(mockCache).setStudy(updatedStudy);
        verify(mockCache).removeStudy(study.getIdentifier());

        try {
            studyService.getStudy(study.getIdentifier());
            fail("Should have thrown an exception");
        } catch(EntityNotFoundException e) {
            // expected exception
        }
        // Verify that all the dependent stuff has been deleted as well:
        assertEquals(0, subpopDao.getSubpopulations(study.getStudyIdentifier(), false, true).size());
        assertEquals(0, studyConsentService.getAllConsents(SubpopulationGuid.create(study.getIdentifier())).size());
        study = null;
    }
    
    @Test
    public void canUpdatePasswordPolicyAndTemplates() {
        study = TestUtils.getValidStudy(StudyServiceTest.class);
        study.setPasswordPolicy(null);
        study.setVerifyEmailTemplate(null);
        study.setResetPasswordTemplate(null);
        study = studyService.createStudy(study);

        // First, verify that defaults are set...
        PasswordPolicy policy = study.getPasswordPolicy();
        assertNotNull(policy);
        assertEquals(8, policy.getMinLength());
        assertTrue(policy.isNumericRequired());
        assertTrue(policy.isSymbolRequired());
        assertTrue(policy.isUpperCaseRequired());

        EmailTemplate veTemplate = study.getVerifyEmailTemplate();
        assertNotNull(veTemplate);
        assertNotNull(veTemplate.getSubject());
        assertNotNull(veTemplate.getBody());
        
        EmailTemplate rpTemplate = study.getResetPasswordTemplate();
        assertNotNull(rpTemplate);
        assertNotNull(rpTemplate.getSubject());
        assertNotNull(rpTemplate.getBody());
        
        SmsTemplate smsTemplate = new SmsTemplate("Test Template ${token} ${appInstallUrl} ${resetPasswordUrl}"); 
        study.setResetPasswordSmsTemplate(smsTemplate);
        study.setPhoneSignInSmsTemplate(smsTemplate);
        study.setAppInstallLinkSmsTemplate(smsTemplate);
        study.setVerifyPhoneSmsTemplate(smsTemplate);
        study.setAccountExistsSmsTemplate(smsTemplate);
        
        // Now change them and verify they are changed.
        study.setPasswordPolicy(new PasswordPolicy(6, true, false, false, true));
        study.setVerifyEmailTemplate(new EmailTemplate("subject *", "body ${url} *", MimeType.TEXT));
        study.setResetPasswordTemplate(new EmailTemplate("subject **", "body ${url} **", MimeType.TEXT));
        
        study = studyService.updateStudy(study, true);
        policy = study.getPasswordPolicy();
        assertTrue(study.isEmailVerificationEnabled());
        assertTrue(study.isAutoVerificationPhoneSuppressed());

        assertEquals(6, policy.getMinLength());
        assertTrue(policy.isNumericRequired());
        assertFalse(policy.isSymbolRequired());
        assertFalse(policy.isLowerCaseRequired());
        assertTrue(policy.isUpperCaseRequired());
        
        veTemplate = study.getVerifyEmailTemplate();
        assertEquals("subject *", veTemplate.getSubject());
        assertEquals("body ${url} *", veTemplate.getBody());
        assertEquals(MimeType.TEXT, veTemplate.getMimeType());
        
        rpTemplate = study.getResetPasswordTemplate();
        assertEquals("subject **", rpTemplate.getSubject());
        assertEquals("body ${url} **", rpTemplate.getBody());
        assertEquals(MimeType.TEXT, rpTemplate.getMimeType());
        
        assertEquals(smsTemplate.getMessage(), study.getResetPasswordSmsTemplate().getMessage());
        assertEquals(smsTemplate.getMessage(), study.getPhoneSignInSmsTemplate().getMessage());
        assertEquals(smsTemplate.getMessage(), study.getAppInstallLinkSmsTemplate().getMessage());
        assertEquals(smsTemplate.getMessage(), study.getVerifyPhoneSmsTemplate().getMessage());
        assertEquals(smsTemplate.getMessage(), study.getAccountExistsSmsTemplate().getMessage());
    }
    
    @Test
    public void defaultsAreUsedWhenNotProvided() {
        study = TestUtils.getValidStudy(StudyServiceTest.class);
        study.setPasswordPolicy(null);
        study.setVerifyEmailTemplate(null);
        study.setResetPasswordTemplate(null);
        study.setEmailSignInTemplate(null);
        study.setAccountExistsTemplate(null);
        study = studyService.createStudy(study);
        
        assertEquals(PasswordPolicy.DEFAULT_PASSWORD_POLICY, study.getPasswordPolicy());
        assertNotNull(study.getVerifyEmailTemplate());
        assertNotNull(study.getResetPasswordTemplate());
        assertNotNull(study.getResetPasswordTemplate().getSubject());
        assertNotNull(study.getResetPasswordTemplate().getBody());
        assertNotNull(study.getEmailSignInTemplate());
        assertNotNull(study.getAccountExistsTemplate());
        
        // Remove them and update... we are set back to defaults
        study.setPasswordPolicy(null);
        study.setVerifyEmailTemplate(null);
        study.setResetPasswordTemplate(null);
        study.setEmailSignInTemplate(null);
        study.setAccountExistsTemplate(null);
        study.setResetPasswordSmsTemplate(null);
        study.setPhoneSignInSmsTemplate(null);
        study.setAppInstallLinkSmsTemplate(null);
        study.setVerifyPhoneSmsTemplate(null);
        study.setAccountExistsSmsTemplate(null);
        study = studyService.updateStudy(study, false);
        
        assertNotNull(study.getVerifyEmailTemplate());
        assertNotNull(study.getResetPasswordTemplate());
        assertNotNull(study.getEmailSignInTemplate());
        assertNotNull(study.getAccountExistsTemplate());
        assertNotNull(study.getResetPasswordSmsTemplate());
        assertNotNull(study.getPhoneSignInSmsTemplate());
        assertNotNull(study.getAppInstallLinkSmsTemplate());
        assertNotNull(study.getVerifyPhoneSmsTemplate());
        assertNotNull(study.getAccountExistsSmsTemplate());
    }
    
    @Test
    public void problematicHtmlIsRemovedFromTemplates() {
        study = TestUtils.getValidStudy(StudyServiceTest.class);
        study.setVerifyEmailTemplate(new EmailTemplate("<b>This is not allowed [ve]</b>", "<p>Test [ve] ${url}</p><script></script>", MimeType.HTML));
        study.setResetPasswordTemplate(new EmailTemplate("<b>This is not allowed [rp]</b>", "<p>Test [rp] ${url}</p>", MimeType.TEXT));
        study = studyService.createStudy(study);
        
        EmailTemplate template = study.getVerifyEmailTemplate();
        assertEquals("This is not allowed [ve]", template.getSubject());
        assertEquals("<p>Test [ve] ${url}</p>", template.getBody());
        assertEquals(MimeType.HTML, template.getMimeType());
        
        template = study.getResetPasswordTemplate();
        assertEquals("This is not allowed [rp]", template.getSubject());
        assertEquals("Test [rp] ${url}", template.getBody());
        assertEquals(MimeType.TEXT, template.getMimeType());
    }
    
    @Test
    public void adminsCanChangeSomeValuesResearchersCannot() {
        study = TestUtils.getValidStudy(StudyServiceTest.class);
        study.setStudyIdExcludedInExport(true);
        study.setEmailVerificationEnabled(true);
        study.setExternalIdValidationEnabled(false);
        study.setExternalIdRequiredOnSignup(false);
        study.setEmailSignInEnabled(false);
        study.setPhoneSignInEnabled(false);
        study.setReauthenticationEnabled(false);
        study.setAccountLimit(0);
        study.setVerifyChannelOnSignInEnabled(false);
        
        study = studyService.createStudy(study);
        study = studyService.getStudy(study.getIdentifier());
        assertStudyDefaults(study); // still set to defaults
        
        // Researchers cannot change these
        changeStudyDefaults(study);
        study = studyService.updateStudy(study, false);
        assertStudyDefaults(study); // nope
        
        // But administrators can change these
        changeStudyDefaults(study);
        study = studyService.updateStudy(study, true);
        // These values have all successfully been changed from the defaults
        assertFalse(study.isStudyIdExcludedInExport());
        assertFalse(study.isEmailVerificationEnabled());
        assertFalse(study.isVerifyChannelOnSignInEnabled());
        assertTrue(study.isAutoVerificationPhoneSuppressed());
        assertTrue(study.isExternalIdValidationEnabled());
        assertTrue(study.isExternalIdRequiredOnSignup());
        assertTrue(study.isEmailSignInEnabled());
        assertTrue(study.isPhoneSignInEnabled());
        assertTrue(study.isReauthenticationEnabled());
        assertEquals(10, study.getAccountLimit());
    }

    private void assertStudyDefaults(Study study) {
        assertTrue(study.isStudyIdExcludedInExport());
        assertTrue(study.isEmailVerificationEnabled());
        assertTrue(study.isVerifyChannelOnSignInEnabled());
        assertFalse(study.isExternalIdValidationEnabled());
        assertFalse(study.isExternalIdRequiredOnSignup());
        assertFalse(study.isEmailSignInEnabled());
        assertFalse(study.isPhoneSignInEnabled());
        assertFalse(study.isReauthenticationEnabled());
        assertEquals(0, study.getAccountLimit());
    }
    
    private void changeStudyDefaults(Study study) {
        study.setStudyIdExcludedInExport(false);
        study.setEmailVerificationEnabled(false);
        study.setVerifyChannelOnSignInEnabled(false);
        study.setExternalIdValidationEnabled(true);
        study.setExternalIdRequiredOnSignup(true);
        study.setEmailSignInEnabled(true);
        study.setPhoneSignInEnabled(true);
        study.setReauthenticationEnabled(true);
        study.setAccountLimit(10);
    }
    
    @Test(expected=InvalidEntityException.class)
    public void updateWithInvalidTemplateIsInvalid() {
        study = TestUtils.getValidStudy(StudyServiceTest.class);
        study = studyService.createStudy(study);
        
        study.setVerifyEmailTemplate(new EmailTemplate(null, null, MimeType.HTML));
        studyService.updateStudy(study, false);
    }

    @Test(expected = UnauthorizedException.class)
    public void cantDeleteApiStudy() {
        studyService.deleteStudy("api", true);
    }
    
    @Test
    public void ckeditorHTMLIsPreserved() {
        study = TestUtils.getValidStudy(StudyServiceTest.class);
        
        String body = "<s>This is a test</s><p style=\"color:red\">of new attributes ${url}.</p><hr>";
        
        EmailTemplate template = new EmailTemplate("Subject", body, MimeType.HTML);
        
        study.setVerifyEmailTemplate(template);
        study.setResetPasswordTemplate(template);
        study = studyService.createStudy(study);
        
        // The templates are pretty-print formatted, so remove that. Otherwise, everything should be
        // preserved.
        
        template = study.getVerifyEmailTemplate();
        assertEquals(body, template.getBody().replaceAll("[\n\t\r]", ""));
        
        template = study.getResetPasswordTemplate();
        assertEquals(body, template.getBody().replaceAll("[\n\t\r]", ""));
    }
}
