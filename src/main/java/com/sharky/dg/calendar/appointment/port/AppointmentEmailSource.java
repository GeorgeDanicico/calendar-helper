package com.sharky.dg.calendar.appointment.port;

import java.util.List;

import com.sharky.dg.calendar.appointment.model.ConnectedAccount;
import com.sharky.dg.calendar.appointment.model.EmailMessageSummary;

public interface AppointmentEmailSource {

	List<EmailMessageSummary> fetchLatestMessages(ConnectedAccount account);
}
