package com.sharky.dg.calendar.google;

import java.time.Instant;
import java.util.UUID;

public record GoogleConnection(
	UUID id,
	UUID userId,
	String email,
	String providerSubject,
	String accessToken,
	String refreshToken,
	Instant accessTokenExpiresAt,
	String scope,
	String connectionType,
	Instant createdAt,
	Instant updatedAt
) {
}
