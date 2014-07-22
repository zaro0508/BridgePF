package controllers;

import java.util.Iterator;
import java.util.Map.Entry;

import org.junit.*;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.models.User;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import play.libs.WS.Response;
import static play.mvc.Http.Status.*;
import static play.test.Helpers.*;
import static org.sagebionetworks.bridge.TestConstants.*;
import static org.junit.Assert.*;

public class UserProfileControllerTest {
    
    private ObjectMapper mapper = new ObjectMapper();

    public UserProfileControllerTest() {
        mapper.setSerializationInclusion(Include.NON_NULL);
    }
    
    @Test
    public void getUserProfileWithNoSessionFails() {
        running(testServer(3333), new TestUtils.FailableRunnable() {
            
            @Override
            public void testCode() throws Exception {
                Response response = TestUtils.getURL(null, PROFILE_URL)
                                        .get()
                                        .get(TIMEOUT);
                
                assertEquals("HTTP Status will be 500", INTERNAL_SERVER_ERROR, response.getStatus());
            }
        });
    }
    
    @Test
    public void getUserProfileSuccess() {
        running(testServer(3333), new TestUtils.FailableRunnable() {
            
            @Override
            public void testCode() throws Exception {
                String sessionToken = TestUtils.signIn();
                
                Response response = TestUtils.getURL(sessionToken, PROFILE_URL)
                                         .get()
                                         .get(TIMEOUT);
                JsonNode payload = response.asJson().get("payload");
                
                int count = 0;
                Iterator<Entry<String, JsonNode>> fields = payload.fields();
                while (fields.hasNext()) {
                    fields.next();
                    count++;
                }
                
                assertEquals("User profile has 5 fields.", count, 5);
                
                TestUtils.signOut();
            }
        });
    }
    
    @Test
    public void updateUserProfileWithNoSession() {
        running(testServer(3333), new TestUtils.FailableRunnable() {
            
            @Override
            public void testCode() throws Exception {
                User user = TestUtils.constructTestUser(TEST1);
                Response response = TestUtils.getURL(null, UPDATE_URL)
                                        .put(mapper.writeValueAsString(user))
                                        .get(TIMEOUT);
                
                assertEquals("HTTP Status should be 500", INTERNAL_SERVER_ERROR, response.getStatus());
            }
        });
    }
    
    @Test
    public void updateUserProfileSuccess() {
        running(testServer(3333), new TestUtils.FailableRunnable() {
            
            @Override
            public void testCode() throws Exception {
                String sessionToken = TestUtils.signIn();
                
                User user = TestUtils.constructTestUser(TEST1);
                Response response = TestUtils.getURL(sessionToken, UPDATE_URL)
                                        .put(mapper.writeValueAsString(user))
                                        .get(TIMEOUT);
                
                assertEquals("HTTP Status should be 200 OK", OK, response.getStatus());

                TestUtils.signOut();
            }
        });
    }
}
