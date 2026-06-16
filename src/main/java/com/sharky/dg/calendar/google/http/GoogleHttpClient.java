package com.sharky.dg.calendar.google.http;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
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
public class GoogleHttpClient {

	private final ObjectMapper objectMapper;
	private final HttpClient httpClient;

	@Inject
	public GoogleHttpClient(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
		this.httpClient = HttpClient.newHttpClient();
	}

	public Map<String, Object> getJson(String url, String accessToken, String failureMessage) {
		var request = HttpRequest.newBuilder(URI.create(url))
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
			.GET()
			.build();
		return sendJson(request, failureMessage);
	}

	public Map<String, Object> postForm(String url, Map<String, String> form, String failureMessage) {
		var request = HttpRequest.newBuilder(URI.create(url))
			.header("Content-Type", "application/x-www-form-urlencoded")
			.POST(HttpRequest.BodyPublishers.ofString(formBody(form)))
			.build();
		return sendJson(request, failureMessage);
	}

	public Map<String, Object> sendJson(HttpRequest request, String failureMessage) {
		try {
			var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				throw new WebApplicationException(
					failureMessage,
					Response.status(Response.Status.BAD_GATEWAY).entity(response.body()).build()
				);
			}
			return objectMapper.readValue(response.body(), new TypeReference<>() {
			});
		}
		catch (IOException exception) {
			throw new WebApplicationException(
				failureMessage,
				exception,
				Response.Status.BAD_GATEWAY
			);
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new WebApplicationException(
				failureMessage,
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
}
