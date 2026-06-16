package com.sharky.dg.calendar.google.auth;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import com.sharky.dg.calendar.google.connection.GoogleConnection;
import com.sharky.dg.calendar.google.connection.GoogleConnectionService;
import com.sharky.dg.calendar.google.http.GoogleHttpClient;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

@ApplicationScoped
public class GoogleOAuthService {

	private final GoogleOAuthConfiguration googleOAuthConfiguration;
	private final GoogleConnectionService googleConnectionService;
	private final GoogleHttpClient googleHttpClient;

	@Inject
	public GoogleOAuthService(
		GoogleOAuthConfiguration googleOAuthConfiguration,
		GoogleConnectionService googleConnectionService,
		GoogleHttpClient googleHttpClient
	) {
		this.googleOAuthConfiguration = googleOAuthConfiguration;
		this.googleConnectionService = googleConnectionService;
		this.googleHttpClient = googleHttpClient;
	}

	public GoogleConnection connect(String code, URI redirectUri) {
		googleOAuthConfiguration.requireConfigured();
		var tokenResponse = exchangeCode(code, redirectUri);
		var accessToken = stringValue(tokenResponse.get("access_token"));
		if (accessToken == null) {
			throw webException(Response.Status.BAD_GATEWAY, "Google token endpoint did not return an access token.");
		}

		var userInfo = fetchUserInfo(accessToken);
		var email = stringValue(userInfo.get("email"));
		var providerSubject = stringValue(userInfo.get("sub"));
		if (email == null) {
			throw webException(Response.Status.BAD_GATEWAY, "Google userinfo response did not include an email address.");
		}
		if (providerSubject == null) {
			providerSubject = email;
		}

		var scope = stringValue(tokenResponse.get("scope"));
		if (scope == null) {
			scope = googleOAuthConfiguration.scopeParameter();
		}

		return googleConnectionService.saveOAuthConnection(
			email,
			providerSubject,
			accessToken,
			stringValue(tokenResponse.get("refresh_token")),
			accessTokenExpiresAt(tokenResponse),
			scope
		);
	}

	private Map<String, Object> exchangeCode(String code, URI redirectUri) {
		var form = new LinkedHashMap<String, String>();
		form.put("grant_type", "authorization_code");
		form.put("code", code);
		form.put("client_id", googleOAuthConfiguration.clientId());
		form.put("client_secret", googleOAuthConfiguration.clientSecret());
		form.put("redirect_uri", redirectUri.toString());

		return googleHttpClient.postForm(
			googleOAuthConfiguration.tokenUri(),
			form,
			"Google authorization code exchange failed."
		);
	}

	private Map<String, Object> fetchUserInfo(String accessToken) {
		return googleHttpClient.getJson(
			googleOAuthConfiguration.userInfoUri(),
			accessToken,
			"Google userinfo request failed."
		);
	}

	private Instant accessTokenExpiresAt(Map<String, Object> tokenResponse) {
		var expiresIn = 3600L;
		if (tokenResponse.get("expires_in") instanceof Number seconds) {
			expiresIn = seconds.longValue();
		}
		return Instant.now().plusSeconds(expiresIn);
	}

	private String stringValue(Object value) {
		return value instanceof String string && !string.isBlank() ? string : null;
	}

	private WebApplicationException webException(Response.Status status, String message) {
		return new WebApplicationException(
			message,
			Response.status(status).entity(message).build()
		);
	}
}
