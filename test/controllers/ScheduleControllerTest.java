package controllers;

import static org.junit.Assert.assertEquals;
import static org.sagebionetworks.bridge.TestConstants.TIMEOUT;
import static org.sagebionetworks.bridge.TestConstants.USER_SCHEDULES_URL;
import static org.sagebionetworks.bridge.TestUtils.waitFor;
import static play.test.Helpers.running;
import static play.test.Helpers.testServer;

import java.util.concurrent.Callable;

import javax.annotation.Resource;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.TestUserAdminHelper;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dao.ScheduleDao;
import org.sagebionetworks.bridge.dao.SchedulePlanDao;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.UserSession;
import org.sagebionetworks.bridge.models.schedules.SchedulePlan;
import org.sagebionetworks.bridge.models.schedules.TestSimpleSchedulePlan;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import play.libs.WS.Response;

import com.fasterxml.jackson.databind.JsonNode;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class ScheduleControllerTest {
    
    @Resource
    private TestUserAdminHelper helper;
    
    @Resource
    private ScheduleDao scheduleDao;
    
    @Resource
    private SchedulePlanDao schedulePlanDao;
    
    private UserSession session;
    
    private SchedulePlan plan;
    
    @Before
    public void before() throws Exception {
        session = helper.createUser(getClass().getSimpleName());
        final User user = session.getUser();
        plan = schedulePlanDao.createSchedulePlan(new TestSimpleSchedulePlan());
        // wait until the schedules are created, as this is asynchronous
        waitFor(new Callable<Boolean>() {
            @Override public Boolean call() throws Exception {
                return (scheduleDao.getSchedules(helper.getTestStudy(), user).size() > 0);
            }
        });
    }
    
    @After
    public void after() throws Exception {
        final User user = session.getUser();
        schedulePlanDao.deleteSchedulePlan(helper.getTestStudy(), plan.getGuid());
        helper.deleteUser(session, getClass().getSimpleName());
        // wait until the schedules are all deleted, as this is asynchronous
        waitFor(new Callable<Boolean>() {
            @Override public Boolean call() throws Exception {
                return (scheduleDao.getSchedules(helper.getTestStudy(), user).size() == 0);
            }
        });
    }

    @Test
    public void canRetrieveSchedulesForAUser() throws Exception {
        running(testServer(3333), new Runnable() {
            public void run() {
                Response response = TestUtils.getURL(session.getSessionToken(), USER_SCHEDULES_URL).get().get(TIMEOUT);
                assertEquals("Response OK", 200, response.getStatus());
                
                JsonNode node = response.asJson();
                assertEquals("There is one schedule", 1, node.get("total").asInt());
                assertEquals("Type is Schedule", "Schedule", node.get("items").get(0).get("type").asText());
            }
        });
    }
    
}
