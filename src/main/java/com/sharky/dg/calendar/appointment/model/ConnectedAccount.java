package com.sharky.dg.calendar.appointment.model;

import java.util.UUID;

public record ConnectedAccount(
	UUID id,
	String email
) {
}
