package com.sharky.dg.calendar.appointment;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sharky.dg.calendar.appointment.model.AppointmentExtraction;
import com.sharky.dg.calendar.appointment.model.ConnectedAccount;
import com.sharky.dg.calendar.appointment.model.EmailMessageSummary;
import com.sharky.dg.calendar.appointment.port.AppointmentCalendarWriter;
import com.sharky.dg.calendar.appointment.port.AppointmentEmailSource;
import com.sharky.dg.calendar.appointment.port.ConnectedAccountProvider;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class AppointmentEmailService {

	private static final Logger log = LoggerFactory.getLogger(AppointmentEmailService.class);

	private final AppointmentExtractionAiService appointmentExtractionAiService;
	private final AppointmentEmailSource appointmentEmailSource;
	private final ConnectedAccountProvider connectedAccountProvider;
	private final AppointmentCalendarWriter appointmentCalendarWriter;
	private final AppointmentCalendarEventFactory appointmentCalendarEventFactory;
	private final boolean mockGmailMessagesEnabled;

	@Inject
	public AppointmentEmailService(
		AppointmentExtractionAiService appointmentExtractionAiService,
		AppointmentEmailSource appointmentEmailSource,
		ConnectedAccountProvider connectedAccountProvider,
		AppointmentCalendarWriter appointmentCalendarWriter,
		AppointmentCalendarEventFactory appointmentCalendarEventFactory,
		@ConfigProperty(name = "app.appointment.mock-gmail-messages.enabled", defaultValue = "true") boolean mockGmailMessagesEnabled
	) {
		this.appointmentExtractionAiService = appointmentExtractionAiService;
		this.appointmentEmailSource = appointmentEmailSource;
		this.connectedAccountProvider = connectedAccountProvider;
		this.appointmentCalendarWriter = appointmentCalendarWriter;
		this.appointmentCalendarEventFactory = appointmentCalendarEventFactory;
		this.mockGmailMessagesEnabled = mockGmailMessagesEnabled;
	}

	@WithSpan("appointment.email.scan-connections")
	public void scanConnections() {
		var connections = connectedAccountProvider.listConnectedAccounts();
		Span.current().setAttribute("app.appointment.connections.count", connections.size());
		if (connections.isEmpty()) {
			log.info("No connections are registered within the application");
			return;
		}

		for (var connection : connections) {
			log.info("Processing connection for user: {}", connection.email());
			scanConnectedAccount(connection);
		}
	}

	private void scanConnectedAccount(ConnectedAccount connection) {
		var span = Span.current();
		setSpanAttributeIfPresent(span, "app.appointment.connection.id", connection.id());
		try {
			var latestMessages = fetchLatestGmailMessages(connection);
			span.setAttribute("app.appointment.gmail.latest_messages.count", latestMessages.size());
			log.info("Google Gmail latest {} messages for {}", Integer.valueOf(latestMessages.size()), connection.email());
			var messagesToProcess = messagesToProcess(connection, latestMessages);
			span.setAttribute("app.appointment.gmail.messages_to_process.count", messagesToProcess.size());
			createCalendarEventsForAppointments(connection, messagesToProcess);
			updateLatestSeenMessage(connection, latestMessages);
		}
		catch (Exception exception) {
			log.info("Skipping Gmail probe for {} because stored Google credentials are not usable.", connection.email(), exception);
		}
	}

	private List<EmailMessageSummary> fetchLatestGmailMessages(ConnectedAccount connection) {
		if (!mockGmailMessagesEnabled) {
			return appointmentEmailSource.fetchLatestMessages(connection);
		}

		log.info("Using mock Gmail appointment messages for {}", connection.email());
		return fetchMockAppointmentGmailMessages();
	}

	private List<EmailMessageSummary> fetchMockAppointmentGmailMessages() {
		var mockRunId = Instant.now().toEpochMilli();
		return List.of(
			new EmailMessageSummary(
				"mock-appointment-dental-%s".formatted(mockRunId),
				"mock-thread-dental",
				"Bright Smile Dental <appointments@example.com>",
				"Dental cleaning appointment confirmed",
				"Thu, 11 Jun 2026 09:15:00 +0300",
				"Your dental cleaning appointment is confirmed for 2026-06-20T14:30:00 at Bright Smile Dental, 123 Main Street."
			),
			new EmailMessageSummary(
				"mock-appointment-physio-%s".formatted(mockRunId),
				"mock-thread-physio",
				"City Physio Clinic <schedule@example.com>",
				"Physical therapy visit reminder",
				"Thu, 11 Jun 2026 10:05:00 +0300",
				"Reminder: your physical therapy appointment is scheduled for 2026-06-19T09:00:00 at City Physio Clinic, Room 4."
			),
			new EmailMessageSummary(
				"mock-fake-physio-%s".formatted(mockRunId),
				"mock-thread-physio",
				"City Physio Clinic <schedule@example.com>",
				"Feedback survey",
				"Thu, 14 Jun 2026 10:05:00 +0300",
				"Hello! we would like you to complete our survey after your visit."
			)
		);
	}

	private void createCalendarEventsForAppointments(
		ConnectedAccount connection,
		List<EmailMessageSummary> messageEntries
	) {
		if (messageEntries.isEmpty()) {
			return;
		}

		var createdEvents = 0;
		for (var message : messageEntries) {
			var subject = stringValue(message.subject());
			var snippet = stringValue(message.snippet());
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

			var event = appointmentCalendarWriter.createCalendarEvent(connection, eventRequest.get());
			createdEvents++;
			Span.current().addEvent("appointment.calendar.event.created");
			log.info("Created Google Calendar event for {} from appointment email: {}", connection.email(), event);
		}
		Span.current().setAttribute("app.appointment.calendar.events_created.count", createdEvents);
	}

	AppointmentExtraction extractAppointment(String emailMessage) {
		return appointmentExtractionAiService.extractAppointment(emailMessage);
	}

	private List<EmailMessageSummary> messagesToProcess(
		ConnectedAccount connection,
		List<EmailMessageSummary> latestMessages
	) {
		if (latestMessages.isEmpty()) {
			return List.of();
		}

		var currentLastSeenMessageId = connectedAccountProvider.findLatestGmailMessageId(connection);
		if (currentLastSeenMessageId.isEmpty()) {
			return latestMessages;
		}

		var currentLastSeen = currentLastSeenMessageId.get();
		var newMessages = new ArrayList<EmailMessageSummary>();
		for (var message : latestMessages) {
			if (currentLastSeen.equals(message.id())) {
				break;
			}
			newMessages.add(message);
		}
		return newMessages;
	}

	private void updateLatestSeenMessage(
		ConnectedAccount connection,
		List<EmailMessageSummary> latestMessages
	) {
		if (latestMessages.isEmpty()) {
			return;
		}

		var currentLastSeenMessageId = connectedAccountProvider.findLatestGmailMessageId(connection);
		var newestMessageId = firstMessageId(latestMessages);
		if (newestMessageId == null || currentLastSeenMessageId.filter(newestMessageId::equals).isPresent()) {
			return;
		}

		log.info("Updating the latest seen message for account {}", connection.id());
		connectedAccountProvider.updateLatestGmailMessageId(connection, newestMessageId);
	}

	private String firstMessageId(List<EmailMessageSummary> messageEntries) {
		var messageId = messageEntries.getFirst().id();
		return messageId != null && !messageId.isBlank() ? messageId : null;
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

	private void setSpanAttributeIfPresent(Span span, String key, Object value) {
		if (value != null) {
			span.setAttribute(key, value.toString());
		}
	}
}
