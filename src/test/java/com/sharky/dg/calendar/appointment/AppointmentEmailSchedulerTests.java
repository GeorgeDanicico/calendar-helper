package com.sharky.dg.calendar.appointment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.sharky.dg.calendar.appointment.guardrails.AppointmentEmailCandidateFilter;

class AppointmentEmailSchedulerTests {

	private final AppointmentEmailCandidateFilter filter = new AppointmentEmailCandidateFilter();

	@Test
	void appointmentCandidateMatchesEnglishAppointmentTerms() {
		assertTrue(filter.isAppointmentCandidate(
			"Dental cleaning appointment confirmed",
			"Your appointment is scheduled for 2026-06-16T14:30:00 at Bright Smile Dental."
		));
	}

	@Test
	void appointmentCandidateMatchesRomanianAppointmentTermsWithDiacritics() {
		assertTrue(filter.isAppointmentCandidate(
			"Confirmarea programării",
			"Programarea dumneavoastră la clinică este pe 16.06.2026 la ora 14:30."
		));
	}

	@Test
	void appointmentCandidateRejectsUnrelatedEmail() {
		assertFalse(filter.isAppointmentCandidate(
			"Weekly newsletter",
			"Here are this week's product updates and articles."
		));
	}
}
