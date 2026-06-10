package com.sharky.dg.calendar.appointment;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class AppointmentExtractionClient {

	private final AppointmentExtractionAiService appointmentExtractionAiService;
	private final String apiKey;

	@Inject
	public AppointmentExtractionClient(
		AppointmentExtractionAiService appointmentExtractionAiService,
		@ConfigProperty(name = "quarkus.langchain4j.openai.api-key", defaultValue = "") String apiKey
	) {
		this.appointmentExtractionAiService = appointmentExtractionAiService;
		this.apiKey = apiKey;
	}

	public AppointmentExtraction extractAppointment(String emailMessage) {
		if (apiKey == null || apiKey.isBlank()) {
			throw new IllegalStateException("OPENAI_API_KEY is required to extract appointments.");
		}

		return appointmentExtractionAiService.extractAppointment(emailMessage);
	}
}
