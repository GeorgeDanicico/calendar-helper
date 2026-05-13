package com.sharky.dg.calendar.appointment;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.sharky.dg.calendar.google.GoogleApiService;
import com.sharky.dg.calendar.google.GoogleConnection;
import com.sharky.dg.calendar.google.GoogleConnectionService;

@Component
public class AppointmentEmailScheduler {

	private static final Logger log = LoggerFactory.getLogger(AppointmentEmailScheduler.class);

	private final ChatClient chatClient;
	private final GoogleApiService googleApiService;
	private final GoogleConnectionService googleConnectionService;
	private final AppointmentCalendarEventFactory appointmentCalendarEventFactory;

	public AppointmentEmailScheduler(
		ChatClient chatClient,
		GoogleApiService googleApiService,
		GoogleConnectionService googleConnectionService,
		AppointmentCalendarEventFactory appointmentCalendarEventFactory
	) {
		this.chatClient = chatClient;
		this.googleApiService = googleApiService;
		this.googleConnectionService = googleConnectionService;
		this.appointmentCalendarEventFactory = appointmentCalendarEventFactory;
	}

	@Scheduled(cron = "${app.openai.chat.scan-cron:*/10 * * * * *}")
	void scanEmailMessageForAppointment() {

		var connections = googleConnectionService.listConnections();
		if (connections.isEmpty()) {
			log.info("No connections are registered within the application");
			return;
		}

		for (var connection : connections) {
			log.info("Processing connection for user: {}", connection.email());
			scanGoogleConnection(connection);
		}
	}

	private void scanGoogleConnection(GoogleConnection connection) {
		try {
			var latestMessages = googleApiService.fetchLatestGmailMessages(connection);
			log.info("Google Gmail latest 10 messages for {}", connection.email());
			var messagesToProcess = messagesToProcess(connection, latestMessages);
			createCalendarEventsForAppointments(connection, messagesToProcess);
			updateLatestSeenMessage(connection, latestMessages);
		}
		catch (Exception exception) {
			log.info("Skipping Gmail probe for {} because stored Google credentials are not usable.", connection.email(), exception);
		}
	}

	private void createCalendarEventsForAppointments(
		GoogleConnection connection,
		List<?> messageEntries
	) {
		if (messageEntries.isEmpty()) {
			return;
		}

		for (var messageEntry : messageEntries) {
			if (!(messageEntry instanceof Map<?, ?> messageMap)) {
				continue;
			}

			var subject = stringValue(messageMap.get("subject"));
			var snippet = stringValue(messageMap.get("snippet"));
			var extractionInput = appointmentExtractionInput(subject, snippet);
			if (extractionInput.isBlank()) {
				continue;
			}

			var extraction = extractAppointment(extractionInput);
			var eventRequest = appointmentCalendarEventFactory.fromExtraction(extraction, subject, snippet);
			if (eventRequest.isEmpty()) {
				log.info("Skipping Calendar write for {} because message is not a dated appointment.", connection.email());
				continue;
			}

			var event = googleApiService.createCalendarEvent(connection, eventRequest.get());
			log.info("Created Google Calendar event for {} from appointment email: {}", connection.email(), event);
		}
	}

	private List<?> messagesToProcess(
		GoogleConnection connection,
		Map<String, Object> latestMessages
	) {
		var messages = latestMessages.get("messages");
		if (!(messages instanceof List<?> messageEntries) || messageEntries.isEmpty()) {
			return List.of();
		}

		var currentLastSeenMessageId = googleConnectionService.findLatestGmailMessageId(connection);
		if (currentLastSeenMessageId.isEmpty()) {
			return messageEntries;
		}

		var currentLastSeen = currentLastSeenMessageId.get();
		var newMessages = new ArrayList<>();
		for (var messageEntry : messageEntries) {
			if (!(messageEntry instanceof Map<?, ?> messageMap)) {
				continue;
			}
			if (currentLastSeen.equals(messageMap.get("id"))) {
				break;
			}
			newMessages.add(messageEntry);
		}
		return newMessages;
	}

	private void updateLatestSeenMessage(
		GoogleConnection connection,
		Map<String, Object> latestMessages
	) {
		var messages = latestMessages.get("messages");
		if (!(messages instanceof List<?> messageEntries) || messageEntries.isEmpty()) {
			return;
		}

		var currentLastSeenMessageId = googleConnectionService.findLatestGmailMessageId(connection);
		var newestMessageId = firstMessageId(messageEntries);
		if (newestMessageId == null || currentLastSeenMessageId.filter(newestMessageId::equals).isPresent()) {
			return;
		}

		googleConnectionService.updateLatestGmailMessageId(connection, newestMessageId);
	}

	private String firstMessageId(List<?> messageEntries) {
		var firstMessage = messageEntries.getFirst();
		if (!(firstMessage instanceof Map<?, ?> messageMap)) {
			return null;
		}

		var messageId = messageMap.get("id");
		return messageId instanceof String id && !id.isBlank() ? id : null;
	}

	private AppointmentExtraction extractAppointment(String emailMessage) {
		return chatClient.prompt()
			.user("""
				Check whether this email message is an appointment and extract the structured fields.

				Email message:
				%s
				""".formatted(emailMessage))
			.call()
			.entity(AppointmentExtraction.class);
	}

	private String appointmentExtractionInput(String subject, String snippet) {
		if ((subject == null || subject.isBlank()) && (snippet == null || snippet.isBlank())) {
			return "";
		}
		if (subject == null || subject.isBlank()) {
			return snippet;
		}
		if (snippet == null || snippet.isBlank()) {
			return subject;
		}
		return """
			Subject: %s

			Snippet:
			%s
			""".formatted(subject, snippet);
	}

	private String stringValue(Object value) {
		return value instanceof String string && !string.isBlank() ? string : null;
	}
}
