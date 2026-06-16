package com.sharky.dg.calendar.google.auth;

import java.util.List;

import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = "app.google.oauth")
public interface GoogleOAuthConfiguration {

	String clientId();

	String clientSecret();

	String authorizationUri();

	String tokenUri();

	String userInfoUri();

	List<String> scopes();

	default String scopeParameter() {
		return String.join(" ", scopes().stream()
			.map(String::trim)
			.filter((scope) -> !scope.isBlank())
			.toList());
	}

	default void requireConfigured() {
		if (clientId() == null || clientId().isBlank() || clientSecret() == null || clientSecret().isBlank()) {
			throw new IllegalStateException("GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET are required for Google OAuth.");
		}
	}
}
