package com.sharky.dg.calendar.appointment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

import com.sharky.dg.calendar.appointment.model.AppointmentExtraction;

class AppointmentExtractionClientTests {

	@Test
	void extractAppointmentRequiresOpenAiApiKey() {
		var aiService = new StubAppointmentExtractionAiService(null);
		var client = new AppointmentExtractionClient(aiService);

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
