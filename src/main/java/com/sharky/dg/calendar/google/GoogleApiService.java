package com.sharky.dg.calendar.google;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.model.CalendarListEntry;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;

@ApplicationScoped
public class GoogleApiService {

	private final GoogleConnectionService googleConnectionService;
	private final GoogleAccessTokenProvider googleAccessTokenProvider;
	private final GoogleCalendarClientFactory googleCalendarClientFactory;
	private final ObjectMapper objectMapper;
	private final HttpClient httpClient;
	private final String calendarId;

	@Inject
	public GoogleApiService(
		GoogleConnectionService googleConnectionService,
		GoogleAccessTokenProvider googleAccessTokenProvider,
		GoogleCalendarClientFactory googleCalendarClientFactory,
		ObjectMapper objectMapper,
		@ConfigProperty(name = "app.calendar.id", defaultValue = "primary") String calendarId
	) {
		this.googleConnectionService = googleConnectionService;
		this.googleAccessTokenProvider = googleAccessTokenProvider;
		this.googleCalendarClientFactory = googleCalendarClientFactory;
		this.objectMapper = objectMapper;
		this.httpClient = HttpClient.newHttpClient();
		this.calendarId = calendarId;
	}

	public Map<String, Object> fetchGmailProfile(String email) {
		var connection = googleConnectionService.requireConnection(email);
		return fetchGmailProfile(connection);
	}

	public Map<String, Object> fetchGmailProfile(GoogleConnection connection) {
		return fetchGoogleResource(
			connection,
			"https://gmail.googleapis.com/gmail/v1/users/me/profile"
		);
	}

	public Map<String, Object> fetchCalendarList(String email) {
		var connection = googleConnectionService.requireConnection(email);
		return fetchCalendarList(connection);
	}

	public Map<String, Object> fetchCalendarList(GoogleConnection connection) {
		try {
			var calendarClient = googleCalendarClientFactory.createClient(connection);
			var calendarList = calendarClient.calendarList().list().execute();
			var items = calendarList.getItems() == null ? List.<CalendarListEntry>of() : calendarList.getItems();
			return Map.of(
				"items",
				items.stream()
					.map(this::calendarListEntryToMap)
					.toList()
			);
		}
		catch (IOException exception) {
			throw new WebApplicationException(
				"Google Calendar list request failed.",
				exception,
				Response.Status.BAD_GATEWAY
			);
		}
	}

	public Map<String, Object> createCalendarEvent(String email, CalendarEventRequest eventRequest) {
		var connection = googleConnectionService.requireConnection(email);
		return createCalendarEvent(connection, eventRequest);
	}

	public Map<String, Object> createCalendarEvent(
		GoogleConnection connection,
		CalendarEventRequest eventRequest
	) {
		try {
			var calendarClient = googleCalendarClientFactory.createClient(connection);
			var event = calendarClient.events()
				.insert(calendarId, calendarEvent(eventRequest))
				.execute();
			return eventToMap(event);
		}
		catch (IOException exception) {
			throw new WebApplicationException(
				"Google Calendar event creation failed.",
				exception,
				Response.Status.BAD_GATEWAY
			);
		}
	}

	public Map<String, Object> fetchLatestGmailMessages(String email) {
		var connection = googleConnectionService.requireConnection(email);
		return fetchLatestGmailMessages(connection);
	}

	public Map<String, Object> fetchLatestGmailMessages(GoogleConnection connection) {
		var response = fetchGoogleResource(
			connection,
			"https://gmail.googleapis.com/gmail/v1/users/me/messages?maxResults=10"
		);

		var messages = response.get("messages");
		if (!(messages instanceof List<?> messageEntries) || messageEntries.isEmpty()) {
			return Map.of("messages", List.of());
		}

		var resolvedMessages = new ArrayList<Map<String, Object>>();
		for (var messageEntry : messageEntries) {
			if (!(messageEntry instanceof Map<?, ?> messageMap)) {
				continue;
			}

			var messageId = messageMap.get("id");
			if (!(messageId instanceof String id) || id.isBlank()) {
				continue;
			}

			var message = fetchGoogleResource(
				connection,
				"https://gmail.googleapis.com/gmail/v1/users/me/messages/%s?format=metadata&metadataHeaders=From&metadataHeaders=Subject&metadataHeaders=Date"
					.formatted(id)
			);
			resolvedMessages.add(summarizeMessage(message));
		}

		return Map.of("messages", resolvedMessages);
	}

	private Map<String, Object> fetchGoogleResource(
		GoogleConnection connection,
		String url
	) {
		var accessToken = googleAccessTokenProvider.getAccessToken(connection);
		var request = HttpRequest.newBuilder(URI.create(url))
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
			.GET()
			.build();

		try {
			var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				throw new WebApplicationException(
					"Google API request failed.",
					Response.status(Response.Status.BAD_GATEWAY).entity(response.body()).build()
				);
			}
			return objectMapper.readValue(response.body(), new TypeReference<>() {
			});
		}
		catch (IOException exception) {
			throw new WebApplicationException(
				"Google API request failed.",
				exception,
				Response.Status.BAD_GATEWAY
			);
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new WebApplicationException(
				"Google API request was interrupted.",
				exception,
				Response.Status.BAD_GATEWAY
			);
		}
	}

	private Event calendarEvent(CalendarEventRequest eventRequest) {
		var event = new Event()
			.setSummary(eventRequest.summary())
			.setStart(eventDateTime(eventRequest.start(), eventRequest.timeZone()))
			.setEnd(eventDateTime(eventRequest.end(), eventRequest.timeZone()));

		if (eventRequest.location() != null && !eventRequest.location().isBlank()) {
			event.setLocation(eventRequest.location());
		}
		if (eventRequest.description() != null && !eventRequest.description().isBlank()) {
			event.setDescription(eventRequest.description());
		}

		return event;
	}

	private EventDateTime eventDateTime(java.time.LocalDateTime localDateTime, String timeZone) {
		var zoneId = ZoneId.of(timeZone);
		var dateTime = localDateTime.atZone(zoneId).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
		return new EventDateTime()
			.setDateTime(new DateTime(dateTime))
			.setTimeZone(timeZone);
	}

	private Map<String, Object> calendarListEntryToMap(CalendarListEntry entry) {
		var calendar = new LinkedHashMap<String, Object>();
		calendar.put("id", entry.getId());
		calendar.put("summary", entry.getSummary());
		calendar.put("description", entry.getDescription());
		calendar.put("primary", entry.getPrimary());
		calendar.put("accessRole", entry.getAccessRole());
		calendar.put("timeZone", entry.getTimeZone());
		return calendar;
	}

	private Map<String, Object> eventToMap(Event event) {
		var response = new LinkedHashMap<String, Object>();
		response.put("id", event.getId());
		response.put("summary", event.getSummary());
		response.put("description", event.getDescription());
		response.put("location", event.getLocation());
		response.put("htmlLink", event.getHtmlLink());
		response.put("status", event.getStatus());
		response.put("start", eventDateTimeToMap(event.getStart()));
		response.put("end", eventDateTimeToMap(event.getEnd()));
		return response;
	}

	private Map<String, Object> eventDateTimeToMap(EventDateTime eventDateTime) {
		if (eventDateTime == null) {
			return Map.of();
		}

		var response = new LinkedHashMap<String, Object>();
		response.put("dateTime", eventDateTime.getDateTime());
		response.put("date", eventDateTime.getDate());
		response.put("timeZone", eventDateTime.getTimeZone());
		return response;
	}

	private Map<String, Object> summarizeMessage(Map<String, Object> message) {
		var summary = new LinkedHashMap<String, Object>();
		summary.put("id", message.get("id"));
		summary.put("threadId", message.get("threadId"));
		summary.put("snippet", message.get("snippet"));

		var headersByName = extractHeadersByName(message);
		summary.put("from", headersByName.get("From"));
		summary.put("subject", headersByName.get("Subject"));
		summary.put("date", headersByName.get("Date"));
		return summary;
	}

	private Map<String, String> extractHeadersByName(Map<String, Object> message) {
		var payload = message.get("payload");
		if (!(payload instanceof Map<?, ?> payloadMap)) {
			return Map.of();
		}

		var headers = payloadMap.get("headers");
		if (!(headers instanceof List<?> headerEntries)) {
			return Map.of();
		}

		var headersByName = new LinkedHashMap<String, String>();
		for (var headerEntry : headerEntries) {
			if (!(headerEntry instanceof Map<?, ?> headerMap)) {
				continue;
			}

			var name = headerMap.get("name");
			var value = headerMap.get("value");
			if (name instanceof String headerName && value instanceof String headerValue) {
				headersByName.put(headerName, headerValue);
			}
		}
		return headersByName;
	}
}
