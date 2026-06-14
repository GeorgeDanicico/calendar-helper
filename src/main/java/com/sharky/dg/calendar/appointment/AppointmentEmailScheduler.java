package com.sharky.dg.calendar.appointment;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class AppointmentEmailScheduler {

	private final AppointmentEmailService appointmentEmailService;

	@Inject
	public AppointmentEmailScheduler(AppointmentEmailService appointmentEmailService) {
		this.appointmentEmailService = appointmentEmailService;
	}

	@Scheduled(cron = "{app.appointment.email-scan-cron}")
	void scanEmailMessageForAppointment() {
		appointmentEmailService.scanConnections();
	}
}
