package com.sharky.dg.calendar.google;

import java.util.LinkedHashMap;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.AuthenticatedPrincipalOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

@Configuration
@EnableWebSecurity
public class GoogleSecurityConfiguration {

	@Bean
	public SecurityFilterChain filterChain(
		HttpSecurity http,
		OAuth2AuthorizationRequestResolver authorizationRequestResolver,
		AuthenticationSuccessHandler authenticationSuccessHandler
	) throws Exception {
		http
			.authorizeHttpRequests((requests) -> requests
				.requestMatchers("/google/login", "/google/status", "/google/test/**").permitAll()
				.anyRequest().authenticated()
			)
			.oauth2Login((login) -> login
				.authorizationEndpoint((endpoint) -> endpoint
					.authorizationRequestResolver(authorizationRequestResolver)
				)
				.successHandler(authenticationSuccessHandler)
			)
			.oauth2Client(Customizer.withDefaults())
			.exceptionHandling((exceptions) -> exceptions
				.authenticationEntryPoint((request, response, exception) ->
					response.sendError(HttpStatus.UNAUTHORIZED.value(), "Google login required.")
				)
			);

		return http.build();
	}

	@Bean
	public OAuth2AuthorizationRequestResolver authorizationRequestResolver(
		ClientRegistrationRepository clientRegistrationRepository
	) {
		var resolver = new DefaultOAuth2AuthorizationRequestResolver(
			clientRegistrationRepository,
			"/oauth2/authorization"
		);
		resolver.setAuthorizationRequestCustomizer((builder) -> {
			var additionalParameters = new LinkedHashMap<String, Object>();
			additionalParameters.put("access_type", "offline");
			additionalParameters.put("prompt", "consent");
			additionalParameters.put("include_granted_scopes", "true");
			builder.additionalParameters(additionalParameters);
		});
		return resolver;
	}

	@Bean
	public OAuth2AuthorizedClientService authorizedClientService(
		ClientRegistrationRepository clientRegistrationRepository
	) {
		return new InMemoryOAuth2AuthorizedClientService(clientRegistrationRepository);
	}

	@Bean
	public OAuth2AuthorizedClientRepository authorizedClientRepository(
		OAuth2AuthorizedClientService authorizedClientService
	) {
		return new AuthenticatedPrincipalOAuth2AuthorizedClientRepository(authorizedClientService);
	}

	@Bean
	public AuthenticationSuccessHandler authenticationSuccessHandler(
		OAuth2AuthorizedClientService authorizedClientService,
		GoogleConnectionService googleConnectionService
	) {
		return (request, response, authentication) -> {
			if (authentication instanceof OAuth2AuthenticationToken oauth2Authentication) {
				var authorizedClient = authorizedClientService.loadAuthorizedClient(
					oauth2Authentication.getAuthorizedClientRegistrationId(),
					oauth2Authentication.getName()
				);
				if (authorizedClient != null) {
					googleConnectionService.saveOAuthConnection(oauth2Authentication, authorizedClient);
				}
			}
			response.sendRedirect("/google/status");
		};
	}
}
