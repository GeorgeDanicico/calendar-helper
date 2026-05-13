package com.sharky.dg.calendar.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenAiChatConfiguration {

	private static final String DEFAULT_SYSTEM_PROMPT = """
		You are an assistant that reviews email messages and determines whether they describe an appointment.
		Extract the result into this schema:
		- appointment: boolean
		- time: ISO-8601 local date-time, or null when no appointment date can be determined
		- appointmentType: short string describing the appointment type, or null
		- location: string with the location if present, or null
		If only a date is present, use 00:00:00 as the time component.
		Return only the structured result requested by the caller.
		""";

	@Bean
	ChatClient openAiChatClient(
		ChatModel chatModel,
		@Value("${app.openai.chat.system-prompt:}") String configuredSystemPrompt
	) {
		var systemPrompt = configuredSystemPrompt == null || configuredSystemPrompt.isBlank()
			? DEFAULT_SYSTEM_PROMPT
			: configuredSystemPrompt;
		return ChatClient.builder(chatModel)
			.defaultSystem(systemPrompt)
			.build();
	}
}
