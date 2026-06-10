package com.sharky.dg.calendar.google;

import java.net.URI;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import com.sharky.dg.calendar.appointment.AppointmentCalendarEventFactory;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.CookieParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.core.UriInfo;

@Path("/")
@ApplicationScoped
public class GoogleController {

	private static final String GOOGLE_OAUTH_STATE_COOKIE = "google_oauth_state";
	private static final SecureRandom SECURE_RANDOM = new SecureRandom();

	private final GoogleApiService googleApiService;
	private final GoogleOAuthService googleOAuthService;
	private final GoogleOAuthConfiguration googleOAuthConfiguration;
	private final GoogleConnectionService googleConnectionService;
	private final AppointmentCalendarEventFactory appointmentCalendarEventFactory;

	@Inject
	public GoogleController(
		GoogleApiService googleApiService,
		GoogleOAuthService googleOAuthService,
		GoogleOAuthConfiguration googleOAuthConfiguration,
		GoogleConnectionService googleConnectionService,
		AppointmentCalendarEventFactory appointmentCalendarEventFactory
	) {
		this.googleApiService = googleApiService;
		this.googleOAuthService = googleOAuthService;
		this.googleOAuthConfiguration = googleOAuthConfiguration;
		this.googleConnectionService = googleConnectionService;
		this.appointmentCalendarEventFactory = appointmentCalendarEventFactory;
	}

	@GET
	@Path("google/login")
	public Response login(@Context UriInfo uriInfo) {
		googleOAuthConfiguration.requireConfigured();
		var state = newState();
		var redirectUri = googleCallbackUri(uriInfo);
		var authorizationUri = UriBuilder.fromUri(googleOAuthConfiguration.authorizationUri())
			.queryParam("response_type", "code")
			.queryParam("client_id", googleOAuthConfiguration.clientId())
			.queryParam("redirect_uri", redirectUri)
			.queryParam("scope", googleOAuthConfiguration.scopeParameter())
			.queryParam("state", state)
			.queryParam("access_type", "offline")
			.queryParam("prompt", "consent")
			.queryParam("include_granted_scopes", "true")
			.build();

		return Response.seeOther(authorizationUri)
			.cookie(stateCookie(state, 600))
			.build();
	}

	@GET
	@Path("login/oauth2/code/google")
	public Response googleCallback(
		@Context UriInfo uriInfo,
		@QueryParam("code") String code,
		@QueryParam("state") String state,
		@QueryParam("error") String error,
		@CookieParam(GOOGLE_OAUTH_STATE_COOKIE) String stateCookie
	) {
		if (error != null && !error.isBlank()) {
			throw badRequest("Google OAuth failed: " + error);
		}
		if (code == null || code.isBlank()) {
			throw badRequest("Google OAuth callback did not include an authorization code.");
		}
		if (state == null || state.isBlank() || stateCookie == null || !state.equals(stateCookie)) {
			throw badRequest("Google OAuth state validation failed.");
		}

		googleOAuthService.connect(code, googleCallbackUri(uriInfo));
		return Response.seeOther(URI.create("/google-auth-success.html"))
			.cookie(stateCookie("", 0))
			.build();
	}

	@GET
	@Path("google/status")
	@Produces(MediaType.APPLICATION_JSON)
	public Map<String, Object> status() {
		var connections = googleConnectionService.listConnections();
		var response = new LinkedHashMap<String, Object>();
		response.put("connected", !connections.isEmpty());
		response.put("connections", connections.stream()
			.map((connection) -> {
				var entry = new LinkedHashMap<String, Object>();
				entry.put("userId", connection.userId());
				entry.put("email", connection.email());
				entry.put("providerSubject", connection.providerSubject());
				entry.put("scope", connection.scope());
				entry.put("accessTokenExpiresAt", connection.accessTokenExpiresAt());
				entry.put("updatedAt", connection.updatedAt());
				return entry;
			})
			.toList());
		return response;
	}

	@GET
	@Path("google/test/gmail")
	@Produces(MediaType.APPLICATION_JSON)
	public Map<String, Object> gmailProfile(@QueryParam("email") String email) {
		return googleApiService.fetchGmailProfile(email);
	}

	@GET
	@Path("google/test/gmail/messages")
	@Produces(MediaType.APPLICATION_JSON)
	public Map<String, Object> gmailMessages(@QueryParam("email") String email) {
		return googleApiService.fetchLatestGmailMessages(email);
	}

	@GET
	@Path("google/test/calendar")
	@Produces(MediaType.APPLICATION_JSON)
	public Map<String, Object> calendarList(@QueryParam("email") String email) {
		return googleApiService.fetchCalendarList(email);
	}

	@POST
	@Path("google/test/calendar/events")
	@Produces(MediaType.APPLICATION_JSON)
	public Map<String, Object> createCalendarEvent(@QueryParam("email") String email) {
		return googleApiService.createCalendarEvent(email, appointmentCalendarEventFactory.sampleEvent());
	}

	private URI googleCallbackUri(UriInfo uriInfo) {
		return uriInfo.getBaseUriBuilder()
			.replacePath("login/oauth2/code/google")
			.build();
	}

	private String newState() {
		var bytes = new byte[32];
		SECURE_RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	private NewCookie stateCookie(String value, int maxAge) {
		return new NewCookie.Builder(GOOGLE_OAUTH_STATE_COOKIE)
			.value(value)
			.path("/")
			.maxAge(maxAge)
			.httpOnly(true)
			.sameSite(NewCookie.SameSite.LAX)
			.build();
	}

	private WebApplicationException badRequest(String message) {
		return new WebApplicationException(
			message,
			Response.status(Response.Status.BAD_REQUEST).entity(message).build()
		);
	}
}
