package org.sagebionetworks.bridge.play.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import play.mvc.Result;

import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.models.DateRange;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.services.UserDataDownloadService;

/** Play controller for User Data Download requests. */
@Controller
public class UserDataDownloadController extends BaseController {
    private UserDataDownloadService userDataDownloadService;

    /** Service handler for User Data Download requests. */
    @Autowired
    public void setUserDataDownloadService(UserDataDownloadService userDataDownloadService) {
        this.userDataDownloadService = userDataDownloadService;
    }

    /**
     * Play handler for requesting user data. User must be authenticated and consented. (Otherwise, they couldn't have
     * any data to download to begin with.)
     */
    public Result requestUserData() throws JsonProcessingException {
        UserSession session = getAuthenticatedAndConsentedSession();
        StudyIdentifier studyIdentifier = session.getStudyIdentifier();
        
        // At least for now, if the user does not have a verified email address, do not allow this service.
        StudyParticipant participant = session.getParticipant();
        boolean verifiedEmail = (participant.getEmail() != null && Boolean.TRUE.equals(participant.getEmailVerified()));
        boolean verifiedPhone = (participant.getPhone() != null && Boolean.TRUE.equals(participant.getPhoneVerified()));
        if (!verifiedEmail && !verifiedPhone) {
            throw new BadRequestException("Cannot request user data, account has no verified email address or phone number.");
        }

        DateRange dateRange = parseJson(request(), DateRange.class);
        userDataDownloadService.requestUserData(studyIdentifier, session.getParticipant().getId(), dateRange);
        return acceptedResult("Request submitted.");
    }
}
