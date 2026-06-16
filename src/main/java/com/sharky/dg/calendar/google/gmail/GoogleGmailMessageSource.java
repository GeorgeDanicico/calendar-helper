package com.sharky.dg.calendar.google.gmail;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.sharky.dg.calendar.appointment.model.ConnectedAccount;
import com.sharky.dg.calendar.appointment.model.EmailMessageSummary;
import com.sharky.dg.calendar.appointment.port.AppointmentEmailSource;
import com.sharky.dg.calendar.google.auth.GoogleAccessTokenProvider;
import com.sharky.dg.calendar.google.connection.GoogleConnection;
import com.sharky.dg.calendar.google.connection.GoogleConnectionService;
import com.sharky.dg.calendar.google.http.GoogleHttpClient;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class GoogleGmailMessageSource implements AppointmentEmailSource {

	private static final String GMAIL_MESSAGES_URL =
		"https://gmail.googleapis.com/gmail/v1/users/me/messages?maxResults=10";
	private static final String GMAIL_MESSAGE_METADATA_URL =
		"https://gmail.googleapis.com/gmail/v1/users/me/messages/%s?format=metadata&metadataHeaders=From&metadataHeaders=Subject&metadataHeaders=Date";

	private final GoogleConnectionService googleConnectionService;
	private final GoogleAccessTokenProvider googleAccessTokenProvider;
	private final GoogleHttpClient googleHttpClient;

	@Inject
	public GoogleGmailMessageSource(
		GoogleConnectionService googleConnectionService,
		GoogleAccessTokenProvider googleAccessTokenProvider,
		GoogleHttpClient googleHttpClient
	) {
		this.googleConnectionService = googleConnectionService;
		this.googleAccessTokenProvider = googleAccessTokenProvider;
		this.googleHttpClient = googleHttpClient;
	}

	@Override
	public List<EmailMessageSummary> fetchLatestMessages(ConnectedAccount account) {
		var connection = googleConnectionService.requireConnection(account);
		var response = fetchGoogleResource(connection, GMAIL_MESSAGES_URL);
		var messages = response.get("messages");
		if (!(messages instanceof List<?> messageEntries) || messageEntries.isEmpty()) {
			return List.of();
		}

		var resolvedMessages = new ArrayList<EmailMessageSummary>();
		for (var messageEntry : messageEntries) {
			if (!(messageEntry instanceof Map<?, ?> messageMap)) {
				continue;
			}

			var messageId = messageMap.get("id");
			if (!(messageId instanceof String id) || id.isBlank()) {
				continue;
			}

			var message = fetchGoogleResource(connection, GMAIL_MESSAGE_METADATA_URL.formatted(id));
			resolvedMessages.add(summarizeMessage(message));
		}

		return resolvedMessages;
	}

	private Map<String, Object> fetchGoogleResource(GoogleConnection connection, String url) {
		var accessToken = googleAccessTokenProvider.getAccessToken(connection);
		return googleHttpClient.getJson(url, accessToken, "Google API request failed.");
	}

	private EmailMessageSummary summarizeMessage(Map<String, Object> message) {
		var headersByName = extractHeadersByName(message);
		return new EmailMessageSummary(
			stringValue(message.get("id")),
			stringValue(message.get("threadId")),
			headersByName.get("From"),
			headersByName.get("Subject"),
			headersByName.get("Date"),
			stringValue(message.get("snippet"))
		);
	}

	private Map<String, String> extractHeadersByName(Map<String, Object> message) {
		var payload = message.get("payload");
		if (!(payload instanceof Map<?, ?> payloadMap)) {
			return Map.of();
		}

		var headers = payloadMap.get("headers");
		if (!(headers instanceof List<?> headerEntries)) {
			return Map.of();
		}

		var headersByName = new LinkedHashMap<String, String>();
		for (var headerEntry : headerEntries) {
			if (!(headerEntry instanceof Map<?, ?> headerMap)) {
				continue;
			}
			var name = headerMap.get("name");
			var value = headerMap.get("value");
			if (name instanceof String headerName && value instanceof String headerValue) {
				headersByName.put(headerName, headerValue);
			}
		}
		return headersByName;
	}

	private String stringValue(Object value) {
		return value instanceof String string && !string.isBlank() ? string : null;
	}
}
