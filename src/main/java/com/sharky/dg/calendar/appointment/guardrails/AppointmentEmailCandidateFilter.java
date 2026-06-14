package com.sharky.dg.calendar.appointment.guardrails;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class AppointmentEmailCandidateFilter {

	private static final List<String> APPOINTMENT_KEYWORDS = List.of(
		"appointment",
		"booking",
		"reservation",
		"consultation",
		"checkup",
		"visit",
		"reminder",
		"confirmation",
		"confirmed",
		"scheduled",
		"reschedule",
		"cancelled",
		"canceled",
		"clinic",
		"doctor",
		"dentist",
		"programare",
		"reprogramare",
		"consult", "consultatie",
		"control",
		"rezervare",
		"reamintire",
		"confirmare",
		"intalnire",
		"medic",
		"stomatolog",
		"clinica",
		"cabinet"
	);

	public boolean isAppointmentCandidate(String emailMessage) {
		var searchText = normalizedKeywordSearchText(emailMessage);
		if (searchText.isBlank()) {
			return false;
		}

		return APPOINTMENT_KEYWORDS.stream()
			.anyMatch(searchText::contains);
	}

	public boolean isAppointmentCandidate(String subject, String snippet) {
		return isAppointmentCandidate("%s %s".formatted(
			subject == null ? "" : subject,
			snippet == null ? "" : snippet
		));
	}

	private String normalizedKeywordSearchText(String text) {
		if (text == null || text.isBlank()) {
			return "";
		}

		var withoutDiacritics = Normalizer.normalize(text, Normalizer.Form.NFD)
			.replaceAll("\\p{M}", "");
		return " " + withoutDiacritics.toLowerCase(Locale.ROOT)
			.replaceAll("[^a-z0-9]+", " ")
			.trim() + " ";
	}
}
