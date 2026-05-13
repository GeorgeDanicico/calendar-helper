package com.sharky.dg.calendar.appointment;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.sharky.dg.calendar.google.CalendarEventRequest;

@Component
public class AppointmentCalendarEventFactory {

	private final String timeZone;
	private final Duration defaultDuration;

	public AppointmentCalendarEventFactory(
		@Value("${app.calendar.time-zone}") String timeZone,
		@Value("${app.calendar.default-duration-minutes:60}") long defaultDurationMinutes
	) {
		this.timeZone = timeZone;
		this.defaultDuration = Duration.ofMinutes(defaultDurationMinutes);
	}

	public Optional<CalendarEventRequest> fromExtraction(
		AppointmentExtraction extraction,
		String subject,
		String snippet
	) {
		if (extraction == null || !extraction.appointment() || extraction.time() == null) {
			return Optional.empty();
		}

		var start = extraction.time();
		return Optional.of(new CalendarEventRequest(
			summary(extraction),
			blankToNull(extraction.location()),
			description(subject, snippet),
			start,
			start.plus(defaultDuration),
			timeZone
		));
	}

	public CalendarEventRequest sampleEvent() {
		var start = LocalDateTime.now().plusDays(1).withSecond(0).withNano(0);
		return new CalendarEventRequest(
			"Appointment",
			null,
			"Sample calendar event created by calendar-helper.",
			start,
			start.plus(defaultDuration),
			timeZone
		);
	}

	private String summary(AppointmentExtraction extraction) {
		var appointmentType = blankToNull(extraction.appointmentType());
		return appointmentType == null ? "Appointment" : appointmentType;
	}

	private String description(String subject, String snippet) {
		var normalizedSubject = blankToNull(subject);
		var normalizedSnippet = blankToNull(snippet);
		if (normalizedSubject == null && normalizedSnippet == null) {
			return "Created from an appointment email by calendar-helper.";
		}
		if (normalizedSubject == null) {
			return "Created from email snippet:\n" + normalizedSnippet;
		}
		if (normalizedSnippet == null) {
			return "Created from email subject:\n" + normalizedSubject;
		}
		return """
			Created from email.

			Subject: %s

			Snippet:
			%s
			""".formatted(normalizedSubject, normalizedSnippet);
	}

	private String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value;
	}
}
