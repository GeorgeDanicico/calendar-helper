package com.sharky.dg.calendar.google;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sharky.dg.calendar.appointment.AppointmentEmailScheduler;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

@ApplicationScoped
public class GoogleConnectionService {

	private static final Logger log = LoggerFactory.getLogger(AppointmentEmailScheduler.class);
	private final GoogleConnectionRepository googleConnectionRepository;

	@Inject
	public GoogleConnectionService(GoogleConnectionRepository googleConnectionRepository) {
		this.googleConnectionRepository = googleConnectionRepository;
	}

	public GoogleConnection saveOAuthConnection(
		String email,
		String providerSubject,
		String accessToken,
		String refreshToken,
		Instant accessTokenExpiresAt,
		String scope
	) {
		if (email == null || email.isBlank()) {
			throw webException(Response.Status.BAD_REQUEST, "Google OAuth response did not include an email address.");
		}
		if (providerSubject == null || providerSubject.isBlank()) {
			providerSubject = email;
		}

		return googleConnectionRepository.upsertGoogleConnection(
			new GoogleConnectionRepository.GoogleOAuthConnectionInput(
				email,
				providerSubject,
				accessToken,
				refreshToken,
				accessTokenExpiresAt,
				scope
			)
		);
	}

	public Optional<GoogleConnection> findConnection(String email) {
		if (email == null || email.isBlank()) {
			log.error("Invalid email {}", email);
			throw new IllegalArgumentException("Invalid email.");
		}
		return googleConnectionRepository.findGoogleConnectionByEmail(email);
	}

	public GoogleConnection requireConnection(String email) {
		return findConnection(email)
			.orElseThrow(() -> webException(
				Response.Status.UNAUTHORIZED,
				"No Google account is connected. Open /google/login first."
			));
	}

	public List<GoogleConnection> listConnections() {
		return googleConnectionRepository.listGoogleConnections();
	}

	public Optional<String> findLatestGmailMessageId(GoogleConnection connection) {
		return googleConnectionRepository.findLatestGmailMessageId(connection.userId());
	}

	public void updateLatestGmailMessageId(GoogleConnection connection, String messageId) {
		googleConnectionRepository.updateLatestGmailMessageId(connection.userId(), messageId);
	}

	private WebApplicationException webException(Response.Status status, String message) {
		return new WebApplicationException(
			message,
			Response.status(status).entity(message).build()
		);
	}
}
