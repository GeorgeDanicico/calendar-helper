package com.sharky.dg.calendar.appointment.model;

import java.time.LocalDateTime;

public record AppointmentExtraction(
	boolean appointment,
	LocalDateTime time,
	String appointmentTitle,
	String summary,
	String appointmentType,
	String location
) {
}
