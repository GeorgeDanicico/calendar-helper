package com.sharky.dg.calendar.google.calendar;

import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import com.sharky.dg.calendar.appointment.model.AppointmentCalendarEventRequest;
import com.sharky.dg.calendar.appointment.model.AppointmentCalendarEventResult;
import com.sharky.dg.calendar.appointment.model.ConnectedAccount;
import com.sharky.dg.calendar.appointment.port.AppointmentCalendarWriter;
import com.sharky.dg.calendar.google.connection.GoogleConnectionService;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

@ApplicationScoped
public class GoogleCalendarWriter implements AppointmentCalendarWriter {

	private final GoogleConnectionService googleConnectionService;
	private final GoogleCalendarClientFactory googleCalendarClientFactory;
	private final String calendarId;

	@Inject
	public GoogleCalendarWriter(
		GoogleConnectionService googleConnectionService,
		GoogleCalendarClientFactory googleCalendarClientFactory,
		@ConfigProperty(name = "app.calendar.id", defaultValue = "primary") String calendarId
	) {
		this.googleConnectionService = googleConnectionService;
		this.googleCalendarClientFactory = googleCalendarClientFactory;
		this.calendarId = calendarId == null || calendarId.isBlank() ? "primary" : calendarId;
	}

	@Override
	@WithSpan("google.calendar.create-event")
	public AppointmentCalendarEventResult createCalendarEvent(
		ConnectedAccount account,
		AppointmentCalendarEventRequest eventRequest
	) {
		try {
			Span.current().setAttribute("app.google.calendar.id", calendarId);
			var connection = googleConnectionService.requireConnection(account);
			var calendarClient = googleCalendarClientFactory.createClient(connection);
			var event = calendarClient.events()
				.insert(calendarId, calendarEvent(eventRequest))
				.execute();
			return eventResult(event);
		}
		catch (IOException exception) {
			throw new WebApplicationException(
				"Google Calendar event creation failed.",
				exception,
				Response.Status.BAD_GATEWAY
			);
		}
	}

	private Event calendarEvent(AppointmentCalendarEventRequest eventRequest) {
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

	private AppointmentCalendarEventResult eventResult(Event event) {
		return new AppointmentCalendarEventResult(
			event.getId(),
			event.getSummary(),
			event.getDescription(),
			event.getLocation(),
			event.getHtmlLink(),
			event.getStatus()
		);
	}
}
