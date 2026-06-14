package com.sharky.dg.calendar.appointment;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.sharky.dg.calendar.appointment.model.AppointmentExtraction;
import com.sharky.dg.calendar.google.CalendarEventRequest;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class AppointmentCalendarEventFactory {

	private final String timeZone;
	private final Duration defaultDuration;

	@Inject
	public AppointmentCalendarEventFactory(
		@ConfigProperty(name = "app.calendar.time-zone") String timeZone,
		@ConfigProperty(name = "app.calendar.default-duration-minutes", defaultValue = "60") long defaultDurationMinutes
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
			extraction.appointmentTitle(),
			blankToNull(extraction.location()),
			extraction.summary(),
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

	private String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value;
	}
}
