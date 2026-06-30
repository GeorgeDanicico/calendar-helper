package com.sharky.dg.calendar.appointment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.sharky.dg.calendar.appointment.model.AppointmentExtraction;
import com.sharky.dg.calendar.appointment.model.ConnectedAccount;
import com.sharky.dg.calendar.appointment.model.EmailMessageSummary;
import com.sharky.dg.calendar.appointment.port.AppointmentEmailSource;
import com.sharky.dg.calendar.appointment.port.ConnectedAccountProvider;

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
		var service = new AppointmentEmailService(aiService, null, null, null, null, false);

		var result = service.extractAppointment("Dentist appointment on 2026-05-13 at 14:30.");

		assertSame(extraction, result);
		assertTrue(aiService.called);
		assertEquals("Dentist appointment on 2026-05-13 at 14:30.", aiService.lastEmailMessage);
	}

	@Test
	void scanConnectionsUsesEmailSourceWhenMockMessagesAreDisabled() {
		var account = new ConnectedAccount(UUID.randomUUID(), "person@example.com");
		var emailSource = new StubAppointmentEmailSource();
		var connectedAccountProvider = new StubConnectedAccountProvider(account);
		var service = new AppointmentEmailService(
			null,
			emailSource,
			connectedAccountProvider,
			null,
			null,
			false
		);

		service.scanConnections();

		assertTrue(emailSource.called);
		assertSame(account, emailSource.lastAccount);
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

	private static class StubAppointmentEmailSource implements AppointmentEmailSource {

		private boolean called;
		private ConnectedAccount lastAccount;

		@Override
		public List<EmailMessageSummary> fetchLatestMessages(ConnectedAccount account) {
			called = true;
			lastAccount = account;
			return List.of();
		}
	}

	private static class StubConnectedAccountProvider implements ConnectedAccountProvider {

		private final ConnectedAccount account;

		private StubConnectedAccountProvider(ConnectedAccount account) {
			this.account = account;
		}

		@Override
		public List<ConnectedAccount> listConnectedAccounts() {
			return List.of(account);
		}

		@Override
		public Optional<String> findLatestGmailMessageId(ConnectedAccount account) {
			return Optional.empty();
		}

		@Override
		public void updateLatestGmailMessageId(ConnectedAccount account, String messageId) {
		}
	}
}
