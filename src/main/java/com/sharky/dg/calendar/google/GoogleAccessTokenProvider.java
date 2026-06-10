package com.sharky.dg.calendar.google;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

@ApplicationScoped
public class GoogleAccessTokenProvider {

	private static final Duration REFRESH_SKEW = Duration.ofMinutes(1);

	private final GoogleConnectionRepository googleConnectionRepository;
	private final GoogleOAuthConfiguration googleOAuthConfiguration;
	private final ObjectMapper objectMapper;
	private final HttpClient httpClient;
	private final Clock clock;

	@Inject
	public GoogleAccessTokenProvider(
		GoogleConnectionRepository googleConnectionRepository,
		GoogleOAuthConfiguration googleOAuthConfiguration,
		ObjectMapper objectMapper
	) {
		this.googleConnectionRepository = googleConnectionRepository;
		this.googleOAuthConfiguration = googleOAuthConfiguration;
		this.objectMapper = objectMapper;
		this.httpClient = HttpClient.newHttpClient();
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

		var request = HttpRequest.newBuilder(URI.create(googleOAuthConfiguration.tokenUri()))
			.header("Content-Type", "application/x-www-form-urlencoded")
			.POST(HttpRequest.BodyPublishers.ofString(formBody(form)))
			.build();

		try {
			var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				throw webException(Response.Status.BAD_GATEWAY, "Google token refresh failed.");
			}

			var responseBody = objectMapper.readValue(response.body(), new TypeReference<Map<String, Object>>() {
			});
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
		catch (IOException exception) {
			throw new WebApplicationException(
				"Google token refresh failed.",
				exception,
				Response.Status.BAD_GATEWAY
			);
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new WebApplicationException(
				"Google token refresh was interrupted.",
				exception,
				Response.Status.BAD_GATEWAY
			);
		}
	}

	private String formBody(Map<String, String> form) {
		return form.entrySet().stream()
			.map((entry) -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
			.collect(Collectors.joining("&"));
	}

	private String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
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
