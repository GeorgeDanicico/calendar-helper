package com.sharky.dg.calendar.google;

import java.util.Arrays;
import java.util.List;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class GoogleOAuthConfiguration {

	private final String clientId;
	private final String clientSecret;
	private final String authorizationUri;
	private final String tokenUri;
	private final String userInfoUri;
	private final String scopes;

	@Inject
	public GoogleOAuthConfiguration(
		@ConfigProperty(name = "app.google.oauth.client-id", defaultValue = "") String clientId,
		@ConfigProperty(name = "app.google.oauth.client-secret", defaultValue = "") String clientSecret,
		@ConfigProperty(name = "app.google.oauth.authorization-uri") String authorizationUri,
		@ConfigProperty(name = "app.google.oauth.token-uri") String tokenUri,
		@ConfigProperty(name = "app.google.oauth.user-info-uri") String userInfoUri,
		@ConfigProperty(name = "app.google.oauth.scopes") String scopes
	) {
		this.clientId = clientId;
		this.clientSecret = clientSecret;
		this.authorizationUri = authorizationUri;
		this.tokenUri = tokenUri;
		this.userInfoUri = userInfoUri;
		this.scopes = scopes;
	}

	public String clientId() {
		return clientId;
	}

	public String clientSecret() {
		return clientSecret;
	}

	public String authorizationUri() {
		return authorizationUri;
	}

	public String tokenUri() {
		return tokenUri;
	}

	public String userInfoUri() {
		return userInfoUri;
	}

	public String scopeParameter() {
		return String.join(" ", scopes());
	}

	public List<String> scopes() {
		return Arrays.stream(scopes.split(","))
			.map(String::trim)
			.filter((scope) -> !scope.isBlank())
			.toList();
	}

	public void requireConfigured() {
		if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
			throw new IllegalStateException("GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET are required for Google OAuth.");
		}
	}
}
