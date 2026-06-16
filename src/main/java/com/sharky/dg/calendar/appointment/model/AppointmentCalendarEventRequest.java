package com.sharky.dg.calendar.appointment.model;

import java.time.LocalDateTime;

public record AppointmentCalendarEventRequest(
	String summary,
	String location,
	String description,
	LocalDateTime start,
	LocalDateTime end,
	String timeZone
) {
}
