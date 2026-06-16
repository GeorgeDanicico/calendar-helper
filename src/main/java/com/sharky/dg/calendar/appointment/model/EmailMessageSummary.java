package com.sharky.dg.calendar.appointment.model;

public record EmailMessageSummary(
	String id,
	String threadId,
	String from,
	String subject,
	String date,
	String snippet
) {
}
