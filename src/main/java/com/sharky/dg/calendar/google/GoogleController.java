package com.sharky.dg.calendar.google;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.view.RedirectView;

import com.sharky.dg.calendar.appointment.AppointmentCalendarEventFactory;

@Controller
public class GoogleController {

	private final GoogleApiService googleApiService;
	private final GoogleConnectionService googleConnectionService;
	private final AppointmentCalendarEventFactory appointmentCalendarEventFactory;

	public GoogleController(
		GoogleApiService googleApiService,
		GoogleConnectionService googleConnectionService,
		AppointmentCalendarEventFactory appointmentCalendarEventFactory
	) {
		this.googleApiService = googleApiService;
		this.googleConnectionService = googleConnectionService;
		this.appointmentCalendarEventFactory = appointmentCalendarEventFactory;
	}

	@GetMapping("/google/login")
	public RedirectView login() {
		return new RedirectView("/oauth2/authorization/google");
	}

	@GetMapping("/google/status")
	@ResponseBody
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

	@GetMapping("/google/test/gmail")
	@ResponseBody
	public Map<String, Object> gmailProfile(@RequestParam(required = false) String email) {
		return googleApiService.fetchGmailProfile(email);
	}

	@GetMapping("/google/test/gmail/messages")
	@ResponseBody
	public Map<String, Object> gmailMessages(@RequestParam(required = false) String email) {
		return googleApiService.fetchLatestGmailMessages(email);
	}

	@GetMapping("/google/test/calendar")
	@ResponseBody
	public Map<String, Object> calendarList(@RequestParam(required = false) String email) {
		return googleApiService.fetchCalendarList(email);
	}

	@PostMapping("/google/test/calendar/events")
	@ResponseBody
	public Map<String, Object> createCalendarEvent(@RequestParam(required = false) String email) {
		return googleApiService.createCalendarEvent(email, appointmentCalendarEventFactory.sampleEvent());
	}
}
