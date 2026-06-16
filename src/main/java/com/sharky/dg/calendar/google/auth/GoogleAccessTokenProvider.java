package com.sharky.dg.calendar.google.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;

import com.sharky.dg.calendar.google.connection.GoogleConnection;
import com.sharky.dg.calendar.google.connection.GoogleConnectionRepository;
import com.sharky.dg.calendar.google.http.GoogleHttpClient;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

@ApplicationScoped
public class GoogleAccessTokenProvider {

	private static final Duration REFRESH_SKEW = Duration.ofMinutes(1);

	private final GoogleConnectionRepository googleConnectionRepository;
	private final GoogleOAuthConfiguration googleOAuthConfiguration;
	private final GoogleHttpClient googleHttpClient;
	private final Clock clock;

	@Inject
	public GoogleAccessTokenProvider(
		GoogleConnectionRepository googleConnectionRepository,
		GoogleOAuthConfiguration googleOAuthConfiguration,
		GoogleHttpClient googleHttpClient
	) {
		this.googleConnectionRepository = googleConnectionRepository;
		this.googleOAuthConfiguration = googleOAuthConfiguration;
		this.googleHttpClient = googleHttpClient;
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
			throw webException(
				Response.Status.UNAUTHORIZED,
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

	private RefreshedToken refreshAccessToken(String refreshToken) {
		googleOAuthConfiguration.requireConfigured();
		var form = new LinkedHashMap<String, String>();
		form.put("grant_type", "refresh_token");
		form.put("refresh_token", refreshToken);
		form.put("client_id", googleOAuthConfiguration.clientId());
		form.put("client_secret", googleOAuthConfiguration.clientSecret());

		var responseBody = googleHttpClient.postForm(
			googleOAuthConfiguration.tokenUri(),
			form,
			"Google token refresh failed."
		);

		if (!(responseBody.get("access_token") instanceof String accessToken) || accessToken.isBlank()) {
			throw webException(
				Response.Status.BAD_GATEWAY,
				"Google token endpoint did not return an access token."
			);
		}

		var expiresInSeconds = 3600L;
		if (responseBody.get("expires_in") instanceof Number expiresIn) {
			expiresInSeconds = expiresIn.longValue();
		}

		return new RefreshedToken(
			accessToken,
			Instant.now(clock).plusSeconds(expiresInSeconds)
		);
	}

	private WebApplicationException webException(Response.Status status, String message) {
		return new WebApplicationException(
			message,
			Response.status(status).entity(message).build()
		);
	}

	private record RefreshedToken(String accessToken, Instant expiresAt) {
	}
}
