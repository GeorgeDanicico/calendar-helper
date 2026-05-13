package com.sharky.dg.calendar.google;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class GoogleConnectionRepository {

	static final String GOOGLE_CONNECTION_TYPE = "GOOGLE";

	private final JdbcClient jdbcClient;

	public GoogleConnectionRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	public GoogleConnection upsertGoogleConnection(GoogleOAuthConnectionInput input) {
		var existingConnection = findByProviderSubject(input.providerSubject());
		var userId = existingConnection
			.map(GoogleConnection::userId)
			.orElseGet(() -> upsertUser(input.email()));
		updateUserEmail(userId, input.email());

		if (existingConnection.isPresent()) {
			updateConnection(existingConnection.get().id(), input);
			return findById(existingConnection.get().id()).orElseThrow();
		}

		var existingUserConnection = findByUserIdAndConnectionType(userId, GOOGLE_CONNECTION_TYPE);
		if (existingUserConnection.isPresent()) {
			updateConnection(existingUserConnection.get().id(), input);
			return findById(existingUserConnection.get().id()).orElseThrow();
		}

		var connectionId = UUID.randomUUID();
		insertConnection(connectionId, userId, input);
		return findById(connectionId).orElseThrow();
	}

	public Optional<GoogleConnection> findLatestGoogleConnection() {
		return jdbcClient.sql(connectionSelectSql() + """
				order by c.updated_at desc
				limit 1
				""")
			.query(this::mapConnection)
			.optional();
	}

	public Optional<GoogleConnection> findGoogleConnectionById(UUID id) {
		return findById(id);
	}

	public Optional<GoogleConnection> findGoogleConnectionByEmail(String email) {
		return jdbcClient.sql(connectionSelectSql() + """
				where lower(u.email) = lower(:email)
				and c.connection_type = :connectionType
				order by c.updated_at desc
				limit 1
				""")
			.param("email", email)
			.param("connectionType", GOOGLE_CONNECTION_TYPE)
			.query(this::mapConnection)
			.optional();
	}

	public List<GoogleConnection> listGoogleConnections() {
		return jdbcClient.sql(connectionSelectSql() + """
				where c.connection_type = :connectionType
				order by c.updated_at desc
				""")
			.param("connectionType", GOOGLE_CONNECTION_TYPE)
			.query(this::mapConnection)
			.list();
	}

	public void updateRefreshedTokens(UUID connectionId, String accessToken, Instant accessTokenExpiresAt) {
		// TODO: Encrypt stored OAuth tokens before using this outside local development.
		jdbcClient.sql("""
				update oauth_connections
				set access_token = :accessToken,
					access_token_expires_at = :accessTokenExpiresAt,
					updated_at = current_timestamp
				where id = :connectionId
				""")
			.param("accessToken", accessToken)
			.param("accessTokenExpiresAt", timestamp(accessTokenExpiresAt))
			.param("connectionId", connectionId)
			.update();
	}

	public Optional<String> findLatestGmailMessageId(UUID userId) {
		return jdbcClient.sql("""
				select last_seen_message_id
				from gmail_latest_messages
				where user_id = :userId
				""")
			.param("userId", userId)
			.query(String.class)
			.optional();
	}

	public void updateLatestGmailMessageId(UUID userId, String messageId) {
		var updated = jdbcClient.sql("""
				update gmail_latest_messages
				set last_seen_message_id = :messageId,
					processed_at = current_timestamp
				where user_id = :userId
				""")
			.param("messageId", messageId)
			.param("userId", userId)
			.update();

		if (updated == 0) {
			jdbcClient.sql("""
					insert into gmail_latest_messages (id, user_id, last_seen_message_id)
					values (:id, :userId, :messageId)
					""")
				.param("id", UUID.randomUUID())
				.param("userId", userId)
				.param("messageId", messageId)
				.update();
		}
	}

	private UUID upsertUser(String email) {
		return findUserIdByEmail(email).orElseGet(() -> {
			var userId = UUID.randomUUID();
			jdbcClient.sql("""
					insert into app_user (id, email)
					values (:id, :email)
					""")
				.param("id", userId)
				.param("email", email)
				.update();
			return userId;
		});
	}

	private Optional<UUID> findUserIdByEmail(String email) {
		return jdbcClient.sql("""
				select id
				from app_user
				where lower(email) = lower(:email)
				""")
			.param("email", email)
			.query(UUID.class)
			.optional();
	}

	private void updateUserEmail(UUID userId, String email) {
		jdbcClient.sql("""
				update app_user
				set email = :email
				where id = :userId
				and email <> :email
				""")
			.param("email", email)
			.param("userId", userId)
			.update();
	}

	private Optional<GoogleConnection> findByProviderSubject(String providerSubject) {
		return jdbcClient.sql(connectionSelectSql() + """
				where c.connection_type = :connectionType
				and c.provider_subject = :providerSubject
				""")
			.param("connectionType", GOOGLE_CONNECTION_TYPE)
			.param("providerSubject", providerSubject)
			.query(this::mapConnection)
			.optional();
	}

	private Optional<GoogleConnection> findByUserIdAndConnectionType(UUID userId, String connectionType) {
		return jdbcClient.sql(connectionSelectSql() + """
				where c.user_id = :userId
				and c.connection_type = :connectionType
				""")
			.param("userId", userId)
			.param("connectionType", connectionType)
			.query(this::mapConnection)
			.optional();
	}

	private Optional<GoogleConnection> findById(UUID id) {
		return jdbcClient.sql(connectionSelectSql() + """
				where c.id = :id
				""")
			.param("id", id)
			.query(this::mapConnection)
			.optional();
	}

	private void insertConnection(UUID connectionId, UUID userId, GoogleOAuthConnectionInput input) {
		// TODO: Encrypt stored OAuth tokens before using this outside local development.
		jdbcClient.sql("""
				insert into oauth_connections (
					id,
					user_id,
					provider_subject,
					access_token,
					refresh_token,
					access_token_expires_at,
					scope,
					connection_type
				)
				values (
					:id,
					:userId,
					:providerSubject,
					:accessToken,
					:refreshToken,
					:accessTokenExpiresAt,
					:scope,
					:connectionType
				)
				""")
			.params(connectionParams(connectionId, userId, input))
			.update();
	}

	private void updateConnection(UUID connectionId, GoogleOAuthConnectionInput input) {
		// TODO: Encrypt stored OAuth tokens before using this outside local development.
		jdbcClient.sql("""
				update oauth_connections
				set provider_subject = :providerSubject,
					access_token = :accessToken,
					refresh_token = coalesce(:refreshToken, refresh_token),
					access_token_expires_at = :accessTokenExpiresAt,
					scope = :scope,
					updated_at = current_timestamp
				where id = :id
				""")
			.params(connectionParams(connectionId, null, input))
			.update();
	}

	private Map<String, ?> connectionParams(
		UUID connectionId,
		UUID userId,
		GoogleOAuthConnectionInput input
	) {
		var params = new LinkedHashMap<String, Object>();
		params.put("id", connectionId);
		params.put("userId", userId);
		params.put("providerSubject", input.providerSubject());
		params.put("accessToken", input.accessToken());
		params.put("refreshToken", input.refreshToken());
		params.put("accessTokenExpiresAt", timestamp(input.accessTokenExpiresAt()));
		params.put("scope", input.scope());
		params.put("connectionType", GOOGLE_CONNECTION_TYPE);
		return params;
	}

	private String connectionSelectSql() {
		return """
			select c.id,
				c.user_id,
				u.email,
				c.provider_subject,
				c.access_token,
				c.refresh_token,
				c.access_token_expires_at,
				c.scope,
				c.connection_type,
				c.created_at,
				c.updated_at
			from oauth_connections c
			join app_user u on u.id = c.user_id
			""";
	}

	private GoogleConnection mapConnection(ResultSet resultSet, int rowNumber) throws SQLException {
		return new GoogleConnection(
			resultSet.getObject("id", UUID.class),
			resultSet.getObject("user_id", UUID.class),
			resultSet.getString("email"),
			resultSet.getString("provider_subject"),
			resultSet.getString("access_token"),
			resultSet.getString("refresh_token"),
			timestampToInstant(resultSet.getTimestamp("access_token_expires_at")),
			resultSet.getString("scope"),
			resultSet.getString("connection_type"),
			timestampToInstant(resultSet.getTimestamp("created_at")),
			timestampToInstant(resultSet.getTimestamp("updated_at"))
		);
	}

	private static Timestamp timestamp(Instant instant) {
		return instant == null ? null : Timestamp.from(instant);
	}

	private static Instant timestampToInstant(Timestamp timestamp) {
		return timestamp == null ? null : timestamp.toInstant();
	}

	public record GoogleOAuthConnectionInput(
		String email,
		String providerSubject,
		String accessToken,
		String refreshToken,
		Instant accessTokenExpiresAt,
		String scope
	) {
	}
}
