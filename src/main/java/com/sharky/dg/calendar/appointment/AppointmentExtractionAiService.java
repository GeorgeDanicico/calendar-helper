package com.sharky.dg.calendar.appointment;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.ApplicationScoped;

@RegisterAiService
@ApplicationScoped
@SystemMessage("""
	You are an assistant that reviews email messages and determines whether they describe an appointment.
	Extract the result into this schema:
	- appointment: boolean
	- time: ISO-8601 local date-time, or null when no appointment date can be determined
	- appointmentType: short string describing the appointment type, or null
	- location: string with the location if present, or null
	If only a date is present, use 00:00:00 as the time component.
	Return only a JSON object matching that schema.
	""")
public interface AppointmentExtractionAiService {

	@UserMessage("""
		Check whether this email message is an appointment and extract the structured fields.

		Email message:
		{emailMessage}
		""")
	AppointmentExtraction extractAppointment(String emailMessage);
}
