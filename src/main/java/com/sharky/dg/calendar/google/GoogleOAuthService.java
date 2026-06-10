package com.sharky.dg.calendar.google;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;

@ApplicationScoped
public class GoogleOAuthService {

	private final GoogleOAuthConfiguration googleOAuthConfiguration;
	private final GoogleConnectionService googleConnectionService;
	private final ObjectMapper objectMapper;
	private final HttpClient httpClient;

	@Inject
	public GoogleOAuthService(
		GoogleOAuthConfiguration googleOAuthConfiguration,
		GoogleConnectionService googleConnectionService,
		ObjectMapper objectMapper
	) {
		this.googleOAuthConfiguration = googleOAuthConfiguration;
		this.googleConnectionService = googleConnectionService;
		this.objectMapper = objectMapper;
		this.httpClient = HttpClient.newHttpClient();
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

		var request = HttpRequest.newBuilder(URI.create(googleOAuthConfiguration.tokenUri()))
			.header("Content-Type", "application/x-www-form-urlencoded")
			.POST(HttpRequest.BodyPublishers.ofString(formBody(form)))
			.build();

		return sendJsonRequest(request, "Google authorization code exchange failed.");
	}

	private Map<String, Object> fetchUserInfo(String accessToken) {
		var request = HttpRequest.newBuilder(URI.create(googleOAuthConfiguration.userInfoUri()))
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
			.GET()
			.build();
		return sendJsonRequest(request, "Google userinfo request failed.");
	}

	private Map<String, Object> sendJsonRequest(HttpRequest request, String failureMessage) {
		try {
			var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				throw webException(Response.Status.BAD_GATEWAY, failureMessage);
			}
			return objectMapper.readValue(response.body(), new TypeReference<>() {
			});
		}
		catch (IOException exception) {
			throw new WebApplicationException(
				failureMessage,
				exception,
				Response.status(Response.Status.BAD_GATEWAY).entity(failureMessage).build()
			);
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new WebApplicationException(
				failureMessage,
				exception,
				Response.status(Response.Status.BAD_GATEWAY).entity(failureMessage).build()
			);
		}
	}

	private Instant accessTokenExpiresAt(Map<String, Object> tokenResponse) {
		var expiresIn = 3600L;
		if (tokenResponse.get("expires_in") instanceof Number seconds) {
			expiresIn = seconds.longValue();
		}
		return Instant.now().plusSeconds(expiresIn);
	}

	private String formBody(Map<String, String> form) {
		return form.entrySet().stream()
			.map((entry) -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
			.collect(Collectors.joining("&"));
	}

	private String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
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
