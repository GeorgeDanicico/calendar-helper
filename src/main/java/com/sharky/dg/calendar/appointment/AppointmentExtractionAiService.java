package com.sharky.dg.calendar.appointment;

import com.sharky.dg.calendar.appointment.guardrails.AppointmentEmailCandidateGuardrail;
import com.sharky.dg.calendar.appointment.model.AppointmentExtraction;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.guardrail.InputGuardrails;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.ApplicationScoped;

@RegisterAiService
@ApplicationScoped
@SystemMessage("""
	You extract appointment details from email messages written in English or Romanian.

	An appointment email may describe a scheduled visit, booking, consultation, meeting, reservation,
	medical appointment, dental appointment, service appointment, reminder, confirmation, cancellation,
	or rescheduling notice.

	Romanian appointment terms may include:
	- programare
	- consultatie
	- control
	- confirmare
	- reamintire
	- rezervare
	- intalnire
	- medic
	- stomatolog
	- clinica

	English appointment terms may include:
	- appointment
	- consultation
	- visit
	- checkup
	- reminder
	- confirmation
	- booking
	- reservation
	- clinic
	- dentist
	- doctor

	Extract the result into this schema:
	- appointment: boolean
	- appointmentTitle: string representing the title of the appointment
	- summary: string representing a short summary of the appointment
	- time: ISO-8601 local date-time, or null when no appointment date can be determined
	- appointmentType: the type of the appointment, like: CREATE - for creating an appointment, UPDATE - for updating an appointment, and DELETE - for deleting one.
	- location: location, address, clinic, office, or meeting place if present, or null

	Rules:
	- Return appointment=true only when the email clearly refers to a real scheduled appointment or booking.
	- If the email mentions an appointment but no date can be determined, return appointment=false and time=null.
	- If only a date is present, use 00:00:00 as the time component.
	- If the email is a cancellation with no replacement date/time, return appointment=false and time=null.
	- If the email is a reschedule confirmation with a new date/time, extract the new date/time.
	- Do not invent missing dates, times, locations, or appointment types.
	Return only a JSON object matching that schema.
	""")
public interface AppointmentExtractionAiService {

	@UserMessage("""
		Check whether this email message is an appointment and extract the structured fields.

		Email message:
		{emailMessage}
		""")
	@InputGuardrails(AppointmentEmailCandidateGuardrail.class)
	@WithSpan("appointment.ai.extract-appointment")
	AppointmentExtraction extractAppointment(String emailMessage);
}
