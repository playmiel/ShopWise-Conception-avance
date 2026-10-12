package com.shopwise.app.shared.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Horloge injectable : les dates fixees par le serveur (date d'une vente) ne dependent pas
 * directement de l'heure systeme, ce qui les rend testables avec une horloge fixe.
 */
@Configuration
public class ClockConfig {

	@Bean
	public Clock clock() {
		return Clock.systemDefaultZone();
	}
}