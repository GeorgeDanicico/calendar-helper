package com.sharky.dg.calendar.appointment.port;

import java.util.List;
import java.util.Optional;

import com.sharky.dg.calendar.appointment.model.ConnectedAccount;

public interface ConnectedAccountProvider {

	List<ConnectedAccount> listConnectedAccounts();

	Optional<String> findLatestGmailMessageId(ConnectedAccount account);

	void updateLatestGmailMessageId(ConnectedAccount account, String messageId);
}
