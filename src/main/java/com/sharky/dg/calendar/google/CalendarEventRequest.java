package com.sharky.dg.calendar.google;

import java.time.LocalDateTime;

public record CalendarEventRequest(
	String summary,
	String location,
	String description,
	LocalDateTime start,
	LocalDateTime end,
	String timeZone
) {
}
