package org.sagebionetworks.bridge.play.controllers;

import java.util.Map;

import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.studies.Study;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;

import play.mvc.Http;
import play.mvc.Result;

@Controller
public class EmailController extends BaseController {
    private final Logger LOG = LoggerFactory.getLogger(EmailController.class);
    
    /**
     * An URL to which a POST can be sent to set the user's email notification preference to "off". Cannot turn email
     * notifications back on through this endpoint. This cannot be part of the public API, because MailChimp doesn't
     * deal with non-200 status codes. The token that is submitted is set in the configuration, and must match to allow
     * this call to succeed. Subject to change without warning or backwards compatibility. 
     */
    public Result unsubscribeFromEmail() throws Exception {
        // We catch and return 200s because MailChimp makes a validation call when configuring the web hook, and if it fails,
        // MailChimp won't persist the configuration. We could also detect the validation call because it has a different
        // User-Agent than the real callbacks, "MailChimp.com WebHook Validator" versus "MailChimp.com", and always return 
        // 200 for that validation call.
        try {
            // Token has to be provided as an URL parameter
            String token = getParameter("token");
            if (token == null || !token.equals(bridgeConfig.getEmailUnsubscribeToken())) {
                throw new BridgeServiceException("No authentication token provided.", HttpStatus.SC_UNAUTHORIZED);
            }
            // Study has to be provided as an URL parameter
            String studyId = getParameter("study");
            if (studyId == null) {
                throw new BadRequestException("Study not found.");
            }
            Study study = studyService.getStudy(studyId);
            
            // MailChimp submits email as data[email]
            String email = getParameter("data[email]");
            if (email == null) {
                email = getParameter("email");
            }
            if (email == null) {
                throw new BadRequestException("Email not found.");
            }
            
            // This should always return a healthCode under normal circumstances.
            AccountId accountId = AccountId.forEmail(study.getIdentifier(), email);
            String healthCode = accountDao.getHealthCodeForAccount(accountId);
            if (healthCode == null) {
                throw new BadRequestException("Email not found.");
            }
            accountDao.editAccount(study.getStudyIdentifier(), healthCode, account -> account.setNotifyByEmail(false));
            
            return ok("You have been unsubscribed from future email.");
        } catch(Throwable throwable) {
            String errorMsg = "Unknown error";
            if (StringUtils.isNotBlank(throwable.getMessage())) {
                errorMsg = throwable.getMessage();
            }
            LOG.error("Error unsubscribing: " + errorMsg, throwable);
            return ok(errorMsg);
        }
    }

    private String getParameter(String paramName) {
        Http.Request request = request();

        Map<String, String[]> queryParamMap = request.queryString();
        if (queryParamMap != null) {
            String[] queryParamValueArray = queryParamMap.get(paramName);
            if (queryParamValueArray != null && queryParamValueArray.length > 0) {
                return queryParamValueArray[0];
            }
        }

        Map<String, String[]> formPostMap = request.body().asFormUrlEncoded();
        if (formPostMap != null) {
            String[] formPostValueArray = formPostMap.get(paramName);
            if (formPostValueArray != null && formPostValueArray.length > 0) {
                return formPostValueArray[0];
            }
        }

        return null;
    }
}
