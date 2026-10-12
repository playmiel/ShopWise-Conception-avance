package com.shopwise.app.security.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.shopwise.app.security.entity.Role;

/** Proprietes shopwise.security.* (voir application.properties). */
@ConfigurationProperties(prefix = "shopwise.security")
public record SecurityProperties(Jwt jwt, List<BootstrapUser> bootstrapUsers) {

	public SecurityProperties {
		bootstrapUsers = bootstrapUsers == null ? List.of() : List.copyOf(bootstrapUsers);
	}

	/** Cle HMAC (Base64), duree de validite et emetteur des jetons. */
	public record Jwt(String secret, Duration expiration, String issuer) {
	}

	/** Compte cree au demarrage s'il n'existe pas. */
	public record BootstrapUser(String username, String password, Role role) {
	}
}