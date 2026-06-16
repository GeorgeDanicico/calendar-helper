package com.sharky.dg.calendar.google.calendar;

import java.io.IOException;
import java.security.GeneralSecurityException;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.google.api.client.auth.oauth2.BearerToken;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.calendar.Calendar;
import com.sharky.dg.calendar.google.auth.GoogleAccessTokenProvider;
import com.sharky.dg.calendar.google.connection.GoogleConnection;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class GoogleCalendarClientFactory {

	private final GoogleAccessTokenProvider googleAccessTokenProvider;
	private final NetHttpTransport httpTransport;
	private final String applicationName;

	@Inject
	public GoogleCalendarClientFactory(
		GoogleAccessTokenProvider googleAccessTokenProvider,
		@ConfigProperty(name = "quarkus.application.name", defaultValue = "calendar-helper") String applicationName
	) {
		this.googleAccessTokenProvider = googleAccessTokenProvider;
		this.applicationName = applicationName;
		this.httpTransport = trustedTransport();
	}

	public Calendar createClient(GoogleConnection connection) {
		var credential = new Credential(BearerToken.authorizationHeaderAccessMethod())
			.setAccessToken(googleAccessTokenProvider.getAccessToken(connection));

		return new Calendar.Builder(
			httpTransport,
			GsonFactory.getDefaultInstance(),
			credential
		)
			.setApplicationName(applicationName)
			.build();
	}

	private NetHttpTransport trustedTransport() {
		try {
			return GoogleNetHttpTransport.newTrustedTransport();
		}
		catch (GeneralSecurityException | IOException exception) {
			throw new IllegalStateException("Failed to initialize Google Calendar HTTP transport.", exception);
		}
	}
}
