package com.sharky.dg.calendar.google;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.sharky.dg.calendar.appointment.AppointmentEmailScheduler;

@Service
public class GoogleConnectionService {

	private static final Logger log = LoggerFactory.getLogger(AppointmentEmailScheduler.class);
	private final GoogleConnectionRepository googleConnectionRepository;

	public GoogleConnectionService(GoogleConnectionRepository googleConnectionRepository) {
		this.googleConnectionRepository = googleConnectionRepository;
	}

	public GoogleConnection saveOAuthConnection(
		OAuth2AuthenticationToken authentication,
		OAuth2AuthorizedClient authorizedClient
	) {
		var principal = authentication.getPrincipal();
		var email = principal.<String>getAttribute("email");
		var providerSubject = principal.<String>getAttribute("sub");

		if (email == null || email.isBlank()) {
			throw new ResponseStatusException(
				HttpStatus.BAD_REQUEST,
				"Google OAuth response did not include an email address."
			);
		}
		if (providerSubject == null || providerSubject.isBlank()) {
			providerSubject = authentication.getName();
		}

		var accessToken = authorizedClient.getAccessToken();
		var refreshToken = authorizedClient.getRefreshToken();
		var scope = accessToken.getScopes().stream()
			.sorted(Comparator.naturalOrder())
			.collect(Collectors.joining(" "));

		return googleConnectionRepository.upsertGoogleConnection(
			new GoogleConnectionRepository.GoogleOAuthConnectionInput(
				email,
				providerSubject,
				accessToken.getTokenValue(),
				refreshToken == null ? null : refreshToken.getTokenValue(),
				accessToken.getExpiresAt(),
				scope
			)
		);
	}

	public Optional<GoogleConnection> findConnection(String email) {
		if (email == null || email.isBlank()) {
			log.error("Invalid email {}", email);
			throw new RuntimeException("Invalid email exception: ");
		}
		return googleConnectionRepository.findGoogleConnectionByEmail(email);
	}

	public GoogleConnection requireConnection(String email) {
		return findConnection(email)
			.orElseThrow(() -> new ResponseStatusException(
				HttpStatus.UNAUTHORIZED,
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
}
