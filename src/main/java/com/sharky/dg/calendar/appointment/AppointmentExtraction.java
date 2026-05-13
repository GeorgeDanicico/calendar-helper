package com.sharky.dg.calendar.appointment;

import java.time.LocalDateTime;

public record AppointmentExtraction(
	boolean appointment,
	LocalDateTime time,
	String appointmentType,
	String location
) {
}
