package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.BridgeConstants.API_STUDY_ID;
import static org.sagebionetworks.bridge.models.accounts.SharingScope.ALL_QUALIFIED_RESEARCHERS;
import static org.sagebionetworks.bridge.models.accounts.SharingScope.NO_SHARING;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.joda.time.DateTimeZone;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.accounts.ConsentStatus;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifier;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

@RunWith(MockitoJUnitRunner.class)
public class SessionUpdateServiceTest {

    @Mock
    private ConsentService mockConsentService;
    
    @Mock
    private CacheProvider mockCacheProvider;
    
    private SessionUpdateService service;
    
    @Before
    public void before() {
        service = new SessionUpdateService();
        service.setConsentService(mockConsentService);
        service.setCacheProvider(mockCacheProvider);
    }
    
    @Test
    public void updateTimeZone() {
        UserSession session = new UserSession();
        DateTimeZone timeZone = DateTimeZone.forOffsetHours(-7);
        
        service.updateTimeZone(session, timeZone);
        
        verify(mockCacheProvider).setUserSession(session);
        assertEquals(timeZone, session.getParticipant().getTimeZone());
    }
    
    @Test
    public void updateLanguage() {
        UserSession session = new UserSession();
        LinkedHashSet<String> languages = new LinkedHashSet<>();
        languages.add("es");
        
        service.updateLanguage(session, languages);
        
        verify(mockCacheProvider).setUserSession(session);
        assertEquals("es", session.getParticipant().getLanguages().iterator().next());
    }
    
    @Test
    public void updateExternalId() {
        UserSession session = new UserSession();
        ExternalIdentifier externalId = ExternalIdentifier.create(API_STUDY_ID, "someExternalId");
        
        service.updateExternalId(session, externalId);
        
        verify(mockCacheProvider).setUserSession(session);
        assertEquals("someExternalId", session.getParticipant().getExternalId());
    }
    
    @Test
    public void updateParticipant() {
        UserSession session = new UserSession();
        StudyParticipant participant = new StudyParticipant.Builder().build();
        
        CriteriaContext context = new CriteriaContext.Builder().withStudyIdentifier(API_STUDY_ID).build();
        
        service.updateParticipant(session, context, participant);
        
        verify(mockCacheProvider).setUserSession(session);
        assertEquals(participant, session.getParticipant());
    }
    
    @Test
    public void updateParticipantWithConsentUpdate() {
        UserSession session = new UserSession();
        StudyParticipant participant = new StudyParticipant.Builder().build();
        CriteriaContext context = new CriteriaContext.Builder().withStudyIdentifier(API_STUDY_ID).build();
        Map<SubpopulationGuid,ConsentStatus> statuses = Maps.newHashMap();
                
        when(mockConsentService.getConsentStatuses(context)).thenReturn(statuses);
        
        service.updateParticipant(session, context, participant);
        
        verify(mockCacheProvider).setUserSession(session);
        assertEquals(participant, session.getParticipant());
        assertEquals(statuses, session.getConsentStatuses());
    }
    
    @Test
    public void updateDataGroups() {
        UserSession session = new UserSession();
        Set<String> dataGroups = Sets.newHashSet("data1");
        CriteriaContext context = new CriteriaContext.Builder()
                .withUserDataGroups(dataGroups)
                .withStudyIdentifier(API_STUDY_ID).build();
        
        service.updateDataGroups(session, context);
        
        verify(mockCacheProvider).setUserSession(session);
        assertEquals(dataGroups, session.getParticipant().getDataGroups());
    }
    
    @Test
    public void updateAllConsents() {
        SubpopulationGuid consentA = SubpopulationGuid.create("consentA");
        SubpopulationGuid consentB = SubpopulationGuid.create("consentB");
        
        Map<SubpopulationGuid,ConsentStatus> consents = Maps.newHashMap();
        consents.put(consentA, new ConsentStatus.Builder().withName("consentA").withGuid(consentA).withConsented(true)
                .withSignedMostRecentConsent(true).build());
        consents.put(consentB, new ConsentStatus.Builder().withName("consentB").withGuid(consentB).withConsented(true)
                .withSignedMostRecentConsent(true).build());
        
        when(mockConsentService.getConsentStatuses(any())).thenReturn(consents);
        
        UserSession session = new UserSession();
        session.setStudyIdentifier(API_STUDY_ID);
        session.setConsentStatuses(consents);
        
        service.updateConsentStatus(session, consents, ALL_QUALIFIED_RESEARCHERS, false);
        
        verify(mockCacheProvider).setUserSession(session);
        assertEquals(ALL_QUALIFIED_RESEARCHERS, session.getParticipant().getSharingScope());
    }
    
    @Test
    public void updateConsentStatus() {
        SubpopulationGuid consentA = SubpopulationGuid.create("consentA");
        SubpopulationGuid consentB = SubpopulationGuid.create("consentB");
        
        Map<SubpopulationGuid,ConsentStatus> consents = Maps.newHashMap();
        consents.put(consentA, new ConsentStatus.Builder().withName("consentA").withGuid(consentA).withConsented(false)
                .withSignedMostRecentConsent(false).build());
        consents.put(consentB, new ConsentStatus.Builder().withName("consentB").withGuid(consentB).withConsented(true)
                .withSignedMostRecentConsent(true).build());
        
        UserSession session = new UserSession();
        session.setConsentStatuses(consents);
        
        service.updateConsentStatus(session, consents, ALL_QUALIFIED_RESEARCHERS, false);
        
        verify(mockCacheProvider).setUserSession(session);
        
        assertEquals(ALL_QUALIFIED_RESEARCHERS, session.getParticipant().getSharingScope());
    }
    
    @Test
    public void updateConsentStatusOptionalConsentWithdrawn() {
        // In this situation, the user's sharing should not be set to NO_SHARING.
        SubpopulationGuid consentA = SubpopulationGuid.create("consentA");
        SubpopulationGuid consentB = SubpopulationGuid.create("consentB");
        
        Map<SubpopulationGuid,ConsentStatus> consents = Maps.newHashMap();
        consents.put(consentA, new ConsentStatus.Builder().withName("consentA").withGuid(consentA).withConsented(false)
                .withSignedMostRecentConsent(false).withRequired(false).build());
        consents.put(consentB, new ConsentStatus.Builder().withName("consentB").withGuid(consentB).withConsented(true)
                .withSignedMostRecentConsent(true).withRequired(true).build());
        
        CriteriaContext context = new CriteriaContext.Builder().withStudyIdentifier(API_STUDY_ID).build();
        
        when(mockConsentService.getConsentStatuses(context)).thenReturn(consents);
        
        UserSession session = new UserSession();
        session.setConsentStatuses(consents);

        service.updateConsentStatus(session, consents, ALL_QUALIFIED_RESEARCHERS, true);
        
        assertEquals(ALL_QUALIFIED_RESEARCHERS, session.getParticipant().getSharingScope());
    }
    
    @Test
    public void updateConsentStatusRequiredConsentWithdrawn() {
        // If a withdrawal causes a user to no longer be in study, should set sharing to NO_SHARING
        // In this situation, the user's sharing should not be set to NO_SHARING.
        SubpopulationGuid consentA = SubpopulationGuid.create("consentA");
        SubpopulationGuid consentB = SubpopulationGuid.create("consentB");
        
        Map<SubpopulationGuid,ConsentStatus> consents = Maps.newHashMap();
        consents.put(consentA, new ConsentStatus.Builder().withName("consentA").withGuid(consentA).withConsented(false)
                .withSignedMostRecentConsent(false).withRequired(true).build());
        consents.put(consentB, new ConsentStatus.Builder().withName("consentB").withGuid(consentB).withConsented(true)
                .withSignedMostRecentConsent(true).withRequired(true).build());
        
        UserSession session = new UserSession();
        session.setStudyIdentifier(API_STUDY_ID);
        session.setParticipant(new StudyParticipant.Builder().withHealthCode("healthCode").build());
        session.setConsentStatuses(consents);

        service.updateConsentStatus(session, consents, ALL_QUALIFIED_RESEARCHERS, true);
        
        assertEquals(NO_SHARING, session.getParticipant().getSharingScope());
    }
    
    @Test
    public void updateSharingScope() {
        UserSession session = new UserSession();
        
        service.updateSharingScope(session, ALL_QUALIFIED_RESEARCHERS);
        
        verify(mockCacheProvider).setUserSession(session);
        assertEquals(ALL_QUALIFIED_RESEARCHERS, session.getParticipant().getSharingScope());
    }
}
