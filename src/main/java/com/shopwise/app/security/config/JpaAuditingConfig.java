package com.shopwise.app.security.config;

import java.util.Optional;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Tracabilite (US 5) : les champs @CreatedBy sont renseignes automatiquement avec l'utilisateur
 * authentifie. Les modules metier n'ont donc aucune dependance vers la securite.
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "securityAuditorAware")
public class JpaAuditingConfig {

	@Bean
	AuditorAware<String> securityAuditorAware() {
		return () -> Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
				.filter(Authentication::isAuthenticated)
				.filter(authentication -> !(authentication instanceof AnonymousAuthenticationToken))
				.map(Authentication::getName);
	}
}