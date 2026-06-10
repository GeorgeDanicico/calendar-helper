package com.sharky.dg.calendar.appointment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

class AppointmentExtractionClientTests {

	@Test
	void extractAppointmentDelegatesToAiServiceWhenApiKeyIsConfigured() {
		var expectedExtraction = new AppointmentExtraction(
			true,
			LocalDateTime.parse("2026-05-13T14:30:00"),
			"Dentist appointment",
			"Bright Dental Clinic"
		);
		var aiService = new StubAppointmentExtractionAiService(expectedExtraction);
		var client = new AppointmentExtractionClient(aiService, "sk-test");

		var extraction = client.extractAppointment("Dentist appointment on 2026-05-13 at 14:30.");

		assertSame(expectedExtraction, extraction);
		assertEquals("Dentist appointment on 2026-05-13 at 14:30.", aiService.lastEmailMessage);
	}

	@Test
	void extractAppointmentRequiresOpenAiApiKey() {
		var aiService = new StubAppointmentExtractionAiService(null);
		var client = new AppointmentExtractionClient(aiService, "");

		var exception = assertThrows(
			IllegalStateException.class,
			() -> client.extractAppointment("Dentist appointment on 2026-05-13 at 14:30.")
		);

		assertEquals("OPENAI_API_KEY is required to extract appointments.", exception.getMessage());
		assertFalse(aiService.called);
	}

	private static class StubAppointmentExtractionAiService implements AppointmentExtractionAiService {

		private final AppointmentExtraction extraction;
		private boolean called;
		private String lastEmailMessage;

		private StubAppointmentExtractionAiService(AppointmentExtraction extraction) {
			this.extraction = extraction;
		}

		@Override
		public AppointmentExtraction extractAppointment(String emailMessage) {
			called = true;
			lastEmailMessage = emailMessage;
			return extraction;
		}
	}
}
