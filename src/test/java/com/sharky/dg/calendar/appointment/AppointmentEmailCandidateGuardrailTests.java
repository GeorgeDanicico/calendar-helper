package com.sharky.dg.calendar.appointment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.sharky.dg.calendar.appointment.guardrails.AppointmentEmailCandidateFilter;
import com.sharky.dg.calendar.appointment.guardrails.AppointmentEmailCandidateGuardrail;

import dev.langchain4j.data.message.UserMessage;

class AppointmentEmailCandidateGuardrailTests {

	private final AppointmentEmailCandidateGuardrail guardrail = new AppointmentEmailCandidateGuardrail(
		new AppointmentEmailCandidateFilter()
	);

	@Test
	void guardrailAllowsAppointmentEmail() {
		var result = guardrail.validate(UserMessage.from("""
			Check whether this email message is an appointment and extract the structured fields.

			Email message:
			Your dental appointment is confirmed for 2026-06-16T14:30:00.
			"""));

		assertTrue(result.isSuccess());
	}

	@Test
	void guardrailRejectsUnrelatedEmailEvenWhenPromptWrapperMentionsAppointment() {
		var result = guardrail.validate(UserMessage.from("""
			Check whether this email message is an appointment and extract the structured fields.

			Email message:
			Here are this week's product updates and articles.
			"""));

		assertFalse(result.isSuccess());
		assertTrue(result.isFatal());
	}
}
