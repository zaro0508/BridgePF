package org.sagebionetworks.bridge.cache;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;

import nl.jqno.equalsverifier.EqualsVerifier;

public class CacheKeysTest {
    
    private static final SubpopulationGuid SUBPOP_GUID = SubpopulationGuid.create("guid");
    
    @Test
    public void equalsHashCode() {
        EqualsVerifier.forClass(CacheKeys.CacheKey.class).allFieldsShouldBeUsed().verify();
    }
    
    @Test(expected = NullPointerException.class)
    public void nullsRejected() {
        CacheKeys.appConfigList(null);
    }
    
    @Test
    public void appConfigList() {
        assertEquals("api:AppConfigList", CacheKeys.appConfigList(TestConstants.TEST_STUDY).toString());
    }
    
    @Test
    public void channelThrottling() {
        assertEquals("userId:email:channel-throttling", CacheKeys.channelThrottling("email", "userId").toString());
    }
    
    @Test
    public void emailSignInRequest() {
        SignIn signIn = new SignIn.Builder().withStudy(TestConstants.TEST_STUDY_IDENTIFIER)
                .withEmail("email@email.com").build();
        assertEquals("email@email.com:api:signInRequest", CacheKeys.emailSignInRequest(signIn).toString());
    }
    
    @Test
    public void emailVerification() {
        assertEquals("email@email.com:emailVerificationStatus", CacheKeys.emailVerification("email@email.com").toString());
    }
    
    @Test
    public void itp() {
        assertEquals("guid:"+TestConstants.PHONE.getNumber()+":api:itp",
                CacheKeys.itp(SUBPOP_GUID, TestConstants.TEST_STUDY, TestConstants.PHONE).toString());
    }
    
    @Test
    public void lock() {
        assertEquals("value:java.lang.String:lock", CacheKeys.lock("value", String.class).toString());
    }
    
    @Test
    public void passwordResetForEmail() {
        assertEquals("sptoken:api", CacheKeys.passwordResetForEmail("sptoken", "api").toString());
    }
    
    @Test
    public void passwordResetForPhone() {
        assertEquals("sptoken:phone:" + TestConstants.PHONE.getNumber(),
                CacheKeys.passwordResetForPhone("sptoken", TestConstants.PHONE.getNumber()).toString());
    }
    
    @Test
    public void phoneSignInRequest() {
        SignIn signIn = new SignIn.Builder().withStudy(TestConstants.TEST_STUDY_IDENTIFIER)
                .withPhone(TestConstants.PHONE).build();
        
        assertEquals(TestConstants.PHONE.getNumber() + ":api:phoneSignInRequest",
                CacheKeys.phoneSignInRequest(signIn).toString());
    }
    
    @Test
    public void requestInfo() {
        assertEquals("userId:request-info", CacheKeys.requestInfo("userId").toString());
    }
    
    @Test
    public void sessionKey() {
        assertEquals("sessionToken:session", CacheKeys.sessionKey("sessionToken").toString());
    }
    
    @Test
    public void study() {
        assertEquals("api:study", CacheKeys.study("api").toString());
    }    
    
    @Test
    public void subpop() {
        assertEquals("guid:api:Subpopulation", CacheKeys.subpop(SUBPOP_GUID, TestConstants.TEST_STUDY).toString());
    }
    
    @Test
    public void subpopList() {
        assertEquals("api:SubpopulationList", CacheKeys.subpopList(TestConstants.TEST_STUDY).toString());
    }
    
    @Test
    public void userSessionKey() {
        assertEquals("userId:session:user", CacheKeys.userSessionKey("userId").toString());
    }
    
    @Test
    public void verificationToken() {
        assertEquals("token", CacheKeys.verificationToken("token").toString());
    }
    
    @Test
    public void viewKey() {
        assertEquals("a:b:StringBuilder:view", CacheKeys.viewKey(StringBuilder.class, "a", "b").toString());
    }
}
