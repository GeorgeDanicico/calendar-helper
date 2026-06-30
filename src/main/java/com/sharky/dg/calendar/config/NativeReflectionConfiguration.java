package com.sharky.dg.calendar.config;

import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.googleapis.json.GoogleJsonErrorContainer;
import com.google.api.services.calendar.model.ConferenceData;
import com.google.api.services.calendar.model.ConferenceParameters;
import com.google.api.services.calendar.model.ConferenceParametersAddOnParameters;
import com.google.api.services.calendar.model.ConferenceProperties;
import com.google.api.services.calendar.model.ConferenceRequestStatus;
import com.google.api.services.calendar.model.ConferenceSolution;
import com.google.api.services.calendar.model.ConferenceSolutionKey;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventAttachment;
import com.google.api.services.calendar.model.EventAttendee;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.api.services.calendar.model.EventReminder;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection(targets = {
	GoogleJsonError.class,
	GoogleJsonError.Details.class,
	GoogleJsonError.ErrorInfo.class,
	GoogleJsonError.ParameterViolations.class,
	GoogleJsonErrorContainer.class,
	ConferenceData.class,
	ConferenceParameters.class,
	ConferenceParametersAddOnParameters.class,
	ConferenceProperties.class,
	ConferenceRequestStatus.class,
	ConferenceSolution.class,
	ConferenceSolutionKey.class,
	Event.class,
	Event.Creator.class,
	Event.ExtendedProperties.class,
	Event.Gadget.class,
	Event.Organizer.class,
	Event.Reminders.class,
	Event.Source.class,
	EventAttachment.class,
	EventAttendee.class,
	EventDateTime.class,
	EventReminder.class
})
final class NativeReflectionConfiguration {

	private NativeReflectionConfiguration() {
	}
}
