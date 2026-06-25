package com.sharky.dg.calendar.appointment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class AppointmentEmailScheduler {
	private static final Logger log = LoggerFactory.getLogger(AppointmentEmailService.class);
	private final AppointmentEmailService appointmentEmailService;

	@Inject
	public AppointmentEmailScheduler(AppointmentEmailService appointmentEmailService) {
		this.appointmentEmailService = appointmentEmailService;
	}

	@Scheduled(cron = "{app.appointment.email-scan-cron}")
	void scanEmailMessageForAppointment() {
		log.info("Starting email scheduled extraction job");
		appointmentEmailService.scanConnections();
	}
}
