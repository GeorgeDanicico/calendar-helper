package com.sharky.dg.calendar.appointment.guardrails;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class AppointmentEmailCandidateGuardrail implements InputGuardrail {

	private static final String EMAIL_MESSAGE_MARKER = "Email message:";

	private final AppointmentEmailCandidateFilter appointmentEmailCandidateFilter;

	@Inject
	public AppointmentEmailCandidateGuardrail(AppointmentEmailCandidateFilter appointmentEmailCandidateFilter) {
		this.appointmentEmailCandidateFilter = appointmentEmailCandidateFilter;
	}

	@Override
	public InputGuardrailResult validate(UserMessage userMessage) {
		if (userMessage == null || !userMessage.hasSingleText()) {
			return fatal("Appointment extraction only accepts a single text email message.");
		}

		var emailMessage = emailMessageText(userMessage.singleText());
		if (appointmentEmailCandidateFilter.isAppointmentCandidate(emailMessage)) {
			return success();
		}

		return fatal("Email message does not look appointment-related.");
	}

	private String emailMessageText(String promptText) {
		var markerIndex = promptText.indexOf(EMAIL_MESSAGE_MARKER);
		if (markerIndex == -1) {
			return promptText;
		}

		return promptText.substring(markerIndex + EMAIL_MESSAGE_MARKER.length());
	}
}
