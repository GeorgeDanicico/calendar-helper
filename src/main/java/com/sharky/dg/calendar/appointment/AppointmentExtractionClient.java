package com.sharky.dg.calendar.appointment;

import com.sharky.dg.calendar.appointment.model.AppointmentExtraction;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class AppointmentExtractionClient {

	private final AppointmentExtractionAiService appointmentExtractionAiService;

	@Inject
	public AppointmentExtractionClient(AppointmentExtractionAiService appointmentExtractionAiService) {
		this.appointmentExtractionAiService = appointmentExtractionAiService;
	}

	public AppointmentExtraction extractAppointment(String emailMessage) {
		return appointmentExtractionAiService.extractAppointment(emailMessage);
	}
}
