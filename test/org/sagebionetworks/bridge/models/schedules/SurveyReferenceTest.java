package org.sagebionetworks.bridge.models.schedules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SurveyReferenceTest {

    private static final String CREATED_ON_STRING = "2015-04-29T23:41:56.231Z";
    
    @Test
    public void correctlyParsesSurveyURL() {
        SurveyReference ref = new SurveyReference("https://webservices.sagebridge.org/api/v1/surveys/AAA-BBB-CCC/revisions/2015-04-29T23:41:56.231Z");
        
        assertEquals("AAA-BBB-CCC", ref.getGuid());
        assertEquals(CREATED_ON_STRING, ref.getCreatedOn());
    }
    
    @Test
    public void correctlyParsesPublishedSurveyURL() {
        SurveyReference ref = new SurveyReference("https://webservices.sagebridge.org/api/v1/surveys/AAA-BBB-CCC/revisions/published");
        
        assertEquals("AAA-BBB-CCC", ref.getGuid());
        assertNull(ref.getCreatedOn());
    }

    @Test(expected = IllegalStateException.class)
    public void throwsExceptionOnCloseButNotCorrectURL() {
        new SurveyReference("https://webservices.sagebridge.org/api/v1/surveys/response/AAA-BBB-CCC");
    }
    
    @Test
    public void correctlyIdentifiesSurveyURL() {
        // This isn't perfect, but it helps.
        assertFalse(SurveyReference.isSurveyRef("https://webservices.sagebridge.org/api/v1/surveys/response/AAA-BBB-CCC"));
        assertFalse(SurveyReference.isSurveyRef("https://webservices.sagebridge.org/api/v1/surveys/response/AAA-BBB-CCC/revisions/DDDD/belgium"));
        assertFalse(SurveyReference.isSurveyRef("/api/v1/surveys/AAA-BBB-CCC/revisions/published"));
        assertTrue(SurveyReference.isSurveyRef("https://webservices.sagebridge.org/api/v1/surveys/AAA-BBB-CCC/revisions/2015-04-29T23:41:56.231Z"));
        assertTrue(SurveyReference.isSurveyRef("https://webservices.sagebridge.org/api/v1/surveys/AAA-BBB-CCC/revisions/published"));
    }
    
}
