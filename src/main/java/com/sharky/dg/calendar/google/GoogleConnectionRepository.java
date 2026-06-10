package com.sharky.dg.calendar.google;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.agroal.api.AgroalDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class GoogleConnectionRepository {

	static final String GOOGLE_CONNECTION_TYPE = "GOOGLE";

	private final AgroalDataSource dataSource;

	@Inject
	public GoogleConnectionRepository(AgroalDataSource dataSource) {
		this.dataSource = dataSource;
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
		return queryOptional(
			connectionSelectSql() + """
				order by c.updated_at desc
				limit 1
				""",
			this::mapConnection
		);
	}

	public Optional<GoogleConnection> findGoogleConnectionById(UUID id) {
		return findById(id);
	}

	public Optional<GoogleConnection> findGoogleConnectionByEmail(String email) {
		return queryOptional(
			connectionSelectSql() + """
				where lower(u.email) = lower(?)
				and c.connection_type = ?
				order by c.updated_at desc
				limit 1
				""",
			(statement) -> {
				statement.setString(1, email);
				statement.setString(2, GOOGLE_CONNECTION_TYPE);
			},
			this::mapConnection
		);
	}

	public List<GoogleConnection> listGoogleConnections() {
		return queryList(
			connectionSelectSql() + """
				where c.connection_type = ?
				order by c.updated_at desc
				""",
			(statement) -> statement.setString(1, GOOGLE_CONNECTION_TYPE),
			this::mapConnection
		);
	}

	public void updateRefreshedTokens(UUID connectionId, String accessToken, Instant accessTokenExpiresAt) {
		// TODO: Encrypt stored OAuth tokens before using this outside local development.
		update(
			"""
			update oauth_connections
			set access_token = ?,
				access_token_expires_at = ?,
				updated_at = current_timestamp
			where id = ?
			""",
			(statement) -> {
				statement.setString(1, accessToken);
				statement.setTimestamp(2, timestamp(accessTokenExpiresAt));
				statement.setObject(3, connectionId);
			}
		);
	}

	public Optional<String> findLatestGmailMessageId(UUID userId) {
		return queryOptional(
			"""
			select last_seen_message_id
			from gmail_latest_messages
			where user_id = ?
			""",
			(statement) -> statement.setObject(1, userId),
			(resultSet) -> resultSet.getString("last_seen_message_id")
		);
	}

	public void updateLatestGmailMessageId(UUID userId, String messageId) {
		var updated = update(
			"""
			update gmail_latest_messages
			set last_seen_message_id = ?,
				processed_at = current_timestamp
			where user_id = ?
			""",
			(statement) -> {
				statement.setString(1, messageId);
				statement.setObject(2, userId);
			}
		);

		if (updated == 0) {
			update(
				"""
				insert into gmail_latest_messages (id, user_id, last_seen_message_id)
				values (?, ?, ?)
				""",
				(statement) -> {
					statement.setObject(1, UUID.randomUUID());
					statement.setObject(2, userId);
					statement.setString(3, messageId);
				}
			);
		}
	}

	private UUID upsertUser(String email) {
		return findUserIdByEmail(email).orElseGet(() -> {
			var userId = UUID.randomUUID();
			update(
				"""
				insert into app_user (id, email)
				values (?, ?)
				""",
				(statement) -> {
					statement.setObject(1, userId);
					statement.setString(2, email);
				}
			);
			return userId;
		});
	}

	private Optional<UUID> findUserIdByEmail(String email) {
		return queryOptional(
			"""
			select id
			from app_user
			where lower(email) = lower(?)
			""",
			(statement) -> statement.setString(1, email),
			(resultSet) -> resultSet.getObject("id", UUID.class)
		);
	}

	private void updateUserEmail(UUID userId, String email) {
		update(
			"""
			update app_user
			set email = ?
			where id = ?
			and email <> ?
			""",
			(statement) -> {
				statement.setString(1, email);
				statement.setObject(2, userId);
				statement.setString(3, email);
			}
		);
	}

	private Optional<GoogleConnection> findByProviderSubject(String providerSubject) {
		return queryOptional(
			connectionSelectSql() + """
				where c.connection_type = ?
				and c.provider_subject = ?
				""",
			(statement) -> {
				statement.setString(1, GOOGLE_CONNECTION_TYPE);
				statement.setString(2, providerSubject);
			},
			this::mapConnection
		);
	}

	private Optional<GoogleConnection> findByUserIdAndConnectionType(UUID userId, String connectionType) {
		return queryOptional(
			connectionSelectSql() + """
				where c.user_id = ?
				and c.connection_type = ?
				""",
			(statement) -> {
				statement.setObject(1, userId);
				statement.setString(2, connectionType);
			},
			this::mapConnection
		);
	}

	private Optional<GoogleConnection> findById(UUID id) {
		return queryOptional(
			connectionSelectSql() + """
				where c.id = ?
				""",
			(statement) -> statement.setObject(1, id),
			this::mapConnection
		);
	}

	private void insertConnection(UUID connectionId, UUID userId, GoogleOAuthConnectionInput input) {
		// TODO: Encrypt stored OAuth tokens before using this outside local development.
		update(
			"""
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
			values (?, ?, ?, ?, ?, ?, ?, ?)
			""",
			(statement) -> {
				statement.setObject(1, connectionId);
				statement.setObject(2, userId);
				statement.setString(3, input.providerSubject());
				statement.setString(4, input.accessToken());
				statement.setString(5, input.refreshToken());
				statement.setTimestamp(6, timestamp(input.accessTokenExpiresAt()));
				statement.setString(7, input.scope());
				statement.setString(8, GOOGLE_CONNECTION_TYPE);
			}
		);
	}

	private void updateConnection(UUID connectionId, GoogleOAuthConnectionInput input) {
		// TODO: Encrypt stored OAuth tokens before using this outside local development.
		update(
			"""
			update oauth_connections
			set provider_subject = ?,
				access_token = ?,
				refresh_token = coalesce(?, refresh_token),
				access_token_expires_at = ?,
				scope = ?,
				updated_at = current_timestamp
			where id = ?
			""",
			(statement) -> {
				statement.setString(1, input.providerSubject());
				statement.setString(2, input.accessToken());
				statement.setString(3, input.refreshToken());
				statement.setTimestamp(4, timestamp(input.accessTokenExpiresAt()));
				statement.setString(5, input.scope());
				statement.setObject(6, connectionId);
			}
		);
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

	private <T> Optional<T> queryOptional(String sql, RowMapper<T> rowMapper) {
		return queryOptional(sql, (statement) -> {
		}, rowMapper);
	}

	private <T> Optional<T> queryOptional(
		String sql,
		PreparedStatementConfigurer configurer,
		RowMapper<T> rowMapper
	) {
		var values = queryList(sql, configurer, rowMapper);
		return values.stream().findFirst();
	}

	private <T> List<T> queryList(
		String sql,
		PreparedStatementConfigurer configurer,
		RowMapper<T> rowMapper
	) {
		try (
			var connection = dataSource.getConnection();
			var statement = connection.prepareStatement(sql)
		) {
			configurer.configure(statement);
			try (var resultSet = statement.executeQuery()) {
				var values = new ArrayList<T>();
				while (resultSet.next()) {
					values.add(rowMapper.map(resultSet));
				}
				return values;
			}
		}
		catch (SQLException exception) {
			throw new IllegalStateException("Database query failed.", exception);
		}
	}

	private int update(String sql, PreparedStatementConfigurer configurer) {
		try (
			var connection = dataSource.getConnection();
			var statement = connection.prepareStatement(sql)
		) {
			configurer.configure(statement);
			return statement.executeUpdate();
		}
		catch (SQLException exception) {
			throw new IllegalStateException("Database update failed.", exception);
		}
	}

	private GoogleConnection mapConnection(ResultSet resultSet) throws SQLException {
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

	private interface PreparedStatementConfigurer {
		void configure(PreparedStatement statement) throws SQLException;
	}

	private interface RowMapper<T> {
		T map(ResultSet resultSet) throws SQLException;
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
