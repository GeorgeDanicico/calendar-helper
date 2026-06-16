package com.sharky.dg.calendar.appointment.port;

import com.sharky.dg.calendar.appointment.model.AppointmentCalendarEventRequest;
import com.sharky.dg.calendar.appointment.model.AppointmentCalendarEventResult;
import com.sharky.dg.calendar.appointment.model.ConnectedAccount;

public interface AppointmentCalendarWriter {

	AppointmentCalendarEventResult createCalendarEvent(
		ConnectedAccount account,
		AppointmentCalendarEventRequest eventRequest
	);
}
