package com.sharky.dg.calendar.appointment;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class AppointmentEmailSchedulerTests {

	@Test
	void scheduledScanDelegatesToScanService() {
		var emailService = new StubAppointmentEmailService();
		var scheduler = new AppointmentEmailScheduler(emailService);

		scheduler.scanEmailMessageForAppointment();

		assertTrue(emailService.called);
	}

	private static class StubAppointmentEmailService extends AppointmentEmailService {

		private boolean called;

		private StubAppointmentEmailService() {
			super(null, null, null, null, null, false);
		}

		@Override
		public void scanConnections() {
			called = true;
		}
	}
}
