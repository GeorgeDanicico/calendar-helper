package com.sharky.dg.calendar.appointment.model;

public record AppointmentCalendarEventResult(
	String id,
	String summary,
	String description,
	String location,
	String htmlLink,
	String status
) {
}
