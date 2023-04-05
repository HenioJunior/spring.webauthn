package com.spring.webauthn;

import com.spring.webauthn.configuration.WebAuthProperties;
import com.spring.webauthn.web.RegistrationService;
import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.data.RelyingPartyIdentity;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class AppApplication {

	public static void main(String[] args) {
		SpringApplication.run(AppApplication.class, args);
	}

	@Bean
	public RelyingParty relyingParty(RegistrationService registrationService,
									 WebAuthProperties properties) {
		RelyingPartyIdentity rpIdentity = RelyingPartyIdentity.builder()
				.id(properties.getHostName())
				.name(properties.getDisplay())
				.build();

		return RelyingParty.builder()
				.identity(rpIdentity)
				.credentialRepository(registrationService)
				.origins(properties.getOrigin())
				.build();
	}
}
