package com.sharky.dg.calendar.appointment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.sharky.dg.calendar.appointment.model.AppointmentExtraction;

class AppointmentEmailServiceTests {

	@Test
	void extractAppointmentDelegatesToAiService() {
		var extraction = new AppointmentExtraction(
			true,
			java.time.LocalDateTime.parse("2026-05-13T14:30:00"),
			"Dentist appointment",
			"Dentist appointment on 2026-05-13 at 14:30.",
			"CREATE",
			"Dental Clinic"
		);
		var aiService = new StubAppointmentExtractionAiService(extraction);
		var service = new AppointmentEmailService(aiService, null, null, null, false);

		var result = service.extractAppointment("Dentist appointment on 2026-05-13 at 14:30.");

		assertSame(extraction, result);
		assertTrue(aiService.called);
		assertEquals("Dentist appointment on 2026-05-13 at 14:30.", aiService.lastEmailMessage);
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
