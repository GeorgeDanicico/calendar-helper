package com.sharky.dg.calendar.appointment;

import java.time.Duration;
import java.util.Optional;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.sharky.dg.calendar.appointment.model.AppointmentCalendarEventRequest;
import com.sharky.dg.calendar.appointment.model.AppointmentExtraction;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.annotations.WithSpan;
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

	@WithSpan("appointment.calendar.build-event-request")
	public Optional<AppointmentCalendarEventRequest> fromExtraction(
		AppointmentExtraction extraction,
		String subject,
		String snippet
	) {
		if (extraction == null || !extraction.appointment() || extraction.time() == null) {
			Span.current().setAttribute("app.appointment.calendar.event_request.created", false);
			return Optional.empty();
		}

		var start = extraction.time();
		Span.current().setAttribute("app.appointment.calendar.event_request.created", true);
		return Optional.of(new AppointmentCalendarEventRequest(
			extraction.appointmentTitle(),
			blankToNull(extraction.location()),
			extraction.summary(),
			start,
			start.plus(defaultDuration),
			timeZone
		));
	}

	private String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value;
	}
}
