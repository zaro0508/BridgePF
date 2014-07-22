package org.sagebionetworks.bridge.services;

import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.UserSession;

import com.stormpath.sdk.account.Account;

public interface UserProfileService {
	
    public User createUserFromAccount(Account account);
    
	public User getUser(UserSession session);
	
	public void updateUser(User user, UserSession session);
	
}
