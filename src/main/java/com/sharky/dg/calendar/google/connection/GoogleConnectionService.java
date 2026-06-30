package com.sharky.dg.calendar.google.connection;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.sharky.dg.calendar.appointment.model.ConnectedAccount;
import com.sharky.dg.calendar.appointment.port.ConnectedAccountProvider;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

@ApplicationScoped
public class GoogleConnectionService implements ConnectedAccountProvider {

	private final GoogleConnectionRepository googleConnectionRepository;

	@Inject
	public GoogleConnectionService(GoogleConnectionRepository googleConnectionRepository) {
		this.googleConnectionRepository = googleConnectionRepository;
	}

	public GoogleConnection saveOAuthConnection(
		String email,
		String providerSubject,
		String accessToken,
		String refreshToken,
		Instant accessTokenExpiresAt,
		String scope
	) {
		if (email == null || email.isBlank()) {
			throw webException(Response.Status.BAD_REQUEST, "Google OAuth response did not include an email address.");
		}
		if (providerSubject == null || providerSubject.isBlank()) {
			providerSubject = email;
		}

		return googleConnectionRepository.upsertGoogleConnection(
			new GoogleConnectionRepository.GoogleOAuthConnectionInput(
				email,
				providerSubject,
				accessToken,
				refreshToken,
				accessTokenExpiresAt,
				scope
			)
		);
	}

	public GoogleConnection requireConnection(ConnectedAccount account) {
		if (account == null || account.id() == null) {
			throw webException(Response.Status.UNAUTHORIZED, "No Google account is connected. Open /google/login first.");
		}
		return requireConnection(account.id());
	}

	public GoogleConnection requireConnection(UUID connectionId) {
		return googleConnectionRepository.findGoogleConnectionById(connectionId)
			.orElseThrow(() -> webException(
				Response.Status.UNAUTHORIZED,
				"No Google account is connected. Open /google/login first."
			));
	}

	public List<GoogleConnection> listConnections() {
		return googleConnectionRepository.listGoogleConnections();
	}

	@Override
	@WithSpan("google.connection.list-connected-accounts")
	public List<ConnectedAccount> listConnectedAccounts() {
		var accounts = listConnections().stream()
			.map((connection) -> new ConnectedAccount(connection.id(), connection.email()))
			.toList();
		Span.current().setAttribute("app.google.connected_accounts.count", accounts.size());
		return accounts;
	}

	@Override
	@WithSpan("google.connection.find-latest-gmail-message-id")
	public Optional<String> findLatestGmailMessageId(ConnectedAccount account) {
		var connection = requireConnection(account);
		var messageId = googleConnectionRepository.findLatestGmailMessageId(connection.userId());
		Span.current().setAttribute("app.google.gmail.latest_message_id.exists", messageId.isPresent());
		return messageId;
	}

	@Override
	@WithSpan("google.connection.update-latest-gmail-message-id")
	public void updateLatestGmailMessageId(ConnectedAccount account, String messageId) {
		var connection = requireConnection(account);
		googleConnectionRepository.updateLatestGmailMessageId(connection.userId(), messageId);
	}

	private WebApplicationException webException(Response.Status status, String message) {
		return new WebApplicationException(
			message,
			Response.status(status).entity(message).build()
		);
	}
}
