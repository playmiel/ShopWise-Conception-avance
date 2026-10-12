package com.shopwise.app.security.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.shopwise.app.security.config.SecurityProperties;
import com.shopwise.app.security.entity.AppUser;
import com.shopwise.app.security.repository.AppUserRepository;

/**
 * Cree au demarrage les comptes declares dans shopwise.security.bootstrap-users s'ils n'existent pas.
 * Les mots de passe viennent de la configuration (surchargeables par variables d'environnement)
 * et sont haches avant stockage : aucun hash ni mot de passe n'est ecrit dans les migrations SQL.
 */
@Component
public class UserBootstrap implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(UserBootstrap.class);

	private final AppUserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final SecurityProperties properties;

	public UserBootstrap(AppUserRepository userRepository, PasswordEncoder passwordEncoder, SecurityProperties properties) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.properties = properties;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		for (SecurityProperties.BootstrapUser user : properties.bootstrapUsers()) {
			if (user.username() == null || user.username().isBlank() || user.password() == null
					|| user.password().isBlank() || user.role() == null) {
				throw new IllegalStateException("Invalid bootstrap user configuration: username, password and role are required");
			}
			if (!userRepository.existsByUsername(user.username())) {
				userRepository.save(new AppUser(user.username(), passwordEncoder.encode(user.password()), user.role()));
				log.info("Bootstrap user '{}' created with role {}", user.username(), user.role());
			}
		}
	}
}