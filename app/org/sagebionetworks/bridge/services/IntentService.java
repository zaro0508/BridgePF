package org.sagebionetworks.bridge.services;

import java.util.List;
import java.util.Map;

import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.cache.CacheKey;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.models.OperatingSystem;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.Phone;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.itp.IntentToParticipate;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.subpopulations.Subpopulation;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.sms.SmsMessageProvider;
import org.sagebionetworks.bridge.validators.IntentToParticipateValidator;
import org.sagebionetworks.bridge.validators.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.common.collect.Iterables;

@Component
public class IntentService {

    private static final String APP_INSTALL_URL_KEY = "appInstallUrl";
    
    /** Hold on to the intent for 4 hours. */
    private static final int EXPIRATION_IN_SECONDS = 4 * 60 * 60;
    
    private StudyService studyService;
    
    private SubpopulationService subpopService;
    
    private ConsentService consentService;
    
    private CacheProvider cacheProvider;
    
    private NotificationsService notificationsService;
    
    private AccountDao accountDao;
    
    private ParticipantService participantService;
    
    @Autowired
    final void setStudyService(StudyService studyService) {
        this.studyService = studyService;
    }
    
    @Autowired
    final void setSubpopulationService(SubpopulationService subpopService) {
        this.subpopService = subpopService;
    }
    
    @Autowired
    final void setConsentService(ConsentService consentService) {
        this.consentService = consentService;
    }
    
    @Autowired
    final void setCacheProvider(CacheProvider cacheProvider) {
        this.cacheProvider = cacheProvider;
    }
    
    @Autowired
    final void setNotificationsService(NotificationsService notificationsService) {
        this.notificationsService = notificationsService;
    }
    
    @Autowired
    final void setAccountDao(AccountDao accountDao) {
        this.accountDao = accountDao;
    }
    
    @Autowired
    final void setParticipantService(ParticipantService participantService) {
        this.participantService = participantService;
    }
    
    public void submitIntentToParticipate(IntentToParticipate intent) {
        Validate.entityThrowingException(IntentToParticipateValidator.INSTANCE, intent);
        
        // If the account exists, do nothing.
        AccountId accountId = AccountId.forPhone(intent.getStudyId(), intent.getPhone());
        Account account = accountDao.getAccount(accountId);
        if (account != null) {
            return;
        }
        
        // validate study exists
        Study study = studyService.getStudy(intent.getStudyId());

        // validate subpopulation exists
        SubpopulationGuid guid = SubpopulationGuid.create(intent.getSubpopGuid());
        subpopService.getSubpopulation(study, guid);
        
        // validate it has not yet been submitted
        CacheKey cacheKey = CacheKey.itp(guid, study.getStudyIdentifier(), intent.getPhone());

        if (cacheProvider.getObject(cacheKey, IntentToParticipate.class) == null) {
            cacheProvider.setObject(cacheKey, intent, EXPIRATION_IN_SECONDS);
            
            // send an app store link to download the app, if we have something to send.
            if (!study.getInstallLinks().isEmpty()) {
                String url = getInstallLink(intent.getOsName(), study.getInstallLinks());
                
                // The URL being sent does not expire. We send with a transaction delivery type because
                // this is a critical step in onboarding through this workflow and message needs to be 
                // sent immediately after consenting.
                SmsMessageProvider provider = new SmsMessageProvider.Builder()
                        .withStudy(study)
                        .withSmsTemplate(study.getAppInstallLinkSmsTemplate())
                        .withTransactionType()
                        .withPhone(intent.getPhone())
                        .withToken(APP_INSTALL_URL_KEY, url).build();
                notificationsService.sendSmsMessage(provider);
            }
        }
    }
    
    public boolean registerIntentToParticipate(Study study, Account account) {
        Phone phone = account.getPhone();
        // Somehow, this is being called but the user has no phone number.
        if (phone == null) {
            return false;
        }
        boolean consentsUpdated = false;
        StudyParticipant participant = null;
        List<Subpopulation> subpops = subpopService.getSubpopulations(study.getStudyIdentifier());
        for (Subpopulation subpop : subpops) {
            CacheKey cacheKey = CacheKey.itp(subpop.getGuid(), study.getStudyIdentifier(), phone);
            IntentToParticipate intent = cacheProvider.getObject(cacheKey, IntentToParticipate.class);
            if (intent != null) {
                if (participant == null) {
                    participant = participantService.getParticipant(study, account.getId(), true);
                }
                consentService.consentToResearch(study, subpop.getGuid(), participant, 
                        intent.getConsentSignature(), intent.getScope(), true);
                cacheProvider.removeObject(cacheKey);
                consentsUpdated = true;
            }
        }
        return consentsUpdated;
    }
    
    protected String getInstallLink(String osName, Map<String,String> installLinks) {
        String installLink = installLinks.get(osName);
        // OS name wasn't submitted or it's wrong, use the universal link
        if (installLink == null) {
            installLink = installLinks.get(OperatingSystem.UNIVERSAL);
        }
        // Don't have a link named "Universal" so just find ANYTHING
        if (installLink == null && !installLinks.isEmpty()) {
            installLink = Iterables.getFirst(installLinks.values(), null);
        }
        return installLink;
    }
}
