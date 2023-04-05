package com.spring.authn;

import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.data.RelyingPartyIdentity;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class AuthnApplication {

	public static void main(String[] args) {
		SpringApplication.run(AuthnApplication.class, args);
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
