package com.sharky.dg.calendar.google;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@Component
public class GoogleAccessTokenProvider {

	private static final String GOOGLE_REGISTRATION_ID = "google";
	private static final Duration REFRESH_SKEW = Duration.ofMinutes(1);

	private final GoogleConnectionRepository googleConnectionRepository;
	private final ClientRegistrationRepository clientRegistrationRepository;
	private final RestClient restClient;
	private final Clock clock;

	public GoogleAccessTokenProvider(
		GoogleConnectionRepository googleConnectionRepository,
		ClientRegistrationRepository clientRegistrationRepository
	) {
		this.googleConnectionRepository = googleConnectionRepository;
		this.clientRegistrationRepository = clientRegistrationRepository;
		this.restClient = RestClient.builder().build();
		this.clock = Clock.systemUTC();
	}

	public String getAccessToken(GoogleConnection connection) {
		if (hasReusableAccessToken(connection)) {
			return connection.accessToken();
		}

		var latestConnection = googleConnectionRepository.findGoogleConnectionById(connection.id())
			.orElse(connection);
		if (hasReusableAccessToken(latestConnection)) {
			return latestConnection.accessToken();
		}

		if (latestConnection.refreshToken() == null || latestConnection.refreshToken().isBlank()) {
			throw new ResponseStatusException(
				HttpStatus.UNAUTHORIZED,
				"Google account does not have a refresh token. Reconnect with /google/login."
			);
		}

		var refreshedToken = refreshAccessToken(latestConnection.refreshToken());
		googleConnectionRepository.updateRefreshedTokens(
			latestConnection.id(),
			refreshedToken.accessToken(),
			refreshedToken.expiresAt()
		);
		return refreshedToken.accessToken();
	}

	private boolean hasReusableAccessToken(GoogleConnection connection) {
		return connection.accessToken() != null
			&& !connection.accessToken().isBlank()
			&& connection.accessTokenExpiresAt() != null
			&& connection.accessTokenExpiresAt().isAfter(Instant.now(clock).plus(REFRESH_SKEW));
	}

	@SuppressWarnings("unchecked")
	private RefreshedToken refreshAccessToken(String refreshToken) {
		var clientRegistration = clientRegistrationRepository.findByRegistrationId(GOOGLE_REGISTRATION_ID);
		var form = new LinkedMultiValueMap<String, String>();
		form.add("grant_type", "refresh_token");
		form.add("refresh_token", refreshToken);
		form.add("client_id", clientRegistration.getClientId());
		form.add("client_secret", clientRegistration.getClientSecret());

		var response = restClient.post()
			.uri(clientRegistration.getProviderDetails().getTokenUri())
			.contentType(MediaType.APPLICATION_FORM_URLENCODED)
			.body(form)
			.retrieve()
			.body(Map.class);

		if (response == null || !(response.get("access_token") instanceof String accessToken)) {
			throw new ResponseStatusException(
				HttpStatus.BAD_GATEWAY,
				"Google token endpoint did not return an access token."
			);
		}

		var expiresInSeconds = 3600L;
		if (response.get("expires_in") instanceof Number expiresIn) {
			expiresInSeconds = expiresIn.longValue();
		}

		return new RefreshedToken(
			accessToken,
			Instant.now(clock).plusSeconds(expiresInSeconds)
		);
	}

	private record RefreshedToken(String accessToken, Instant expiresAt) {
	}
}
