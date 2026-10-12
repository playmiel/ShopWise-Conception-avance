package com.shopwise.app.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.shopwise.app.security.config.SecurityProperties;
import com.shopwise.app.security.entity.AppUser;
import com.shopwise.app.security.entity.Role;
import com.shopwise.app.security.repository.AppUserRepository;
import com.shopwise.app.security.service.UserBootstrap;

@ExtendWith(MockitoExtension.class)
@DisplayName("Unitaire - UserBootstrap")
class UserBootstrapTest {

	@Mock
	private AppUserRepository repository;

	private final PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

	private UserBootstrap bootstrap(SecurityProperties.BootstrapUser... users) {
		return new UserBootstrap(repository, encoder, new SecurityProperties(null, List.of(users)));
	}

	@Test
	@DisplayName("cree un compte absent avec un mot de passe hache (jamais en clair)")
	void createsMissingUserWithHashedPassword() {
		when(repository.existsByUsername("marie")).thenReturn(false);

		bootstrap(new SecurityProperties.BootstrapUser("marie", "s3cret!", Role.MERCHANT)).run(null);

		ArgumentCaptor<AppUser> saved = ArgumentCaptor.forClass(AppUser.class);
		verify(repository).save(saved.capture());
		assertThat(saved.getValue().getPasswordHash()).startsWith("{bcrypt}").doesNotContain("s3cret!");
		assertThat(encoder.matches("s3cret!", saved.getValue().getPasswordHash())).isTrue();
		assertThat(saved.getValue().getRole()).isEqualTo(Role.MERCHANT);
	}

	@Test
	@DisplayName("idempotent : un compte existant n'est ni recree ni modifie")
	void keepsExistingUser() {
		when(repository.existsByUsername("admin")).thenReturn(true);

		bootstrap(new SecurityProperties.BootstrapUser("admin", "other", Role.ADMIN)).run(null);

		verify(repository, never()).save(any());
	}

	@Test
	@DisplayName("configuration incomplete : echec explicite au demarrage")
	void rejectsIncompleteConfiguration() {
		assertThatThrownBy(() -> bootstrap(new SecurityProperties.BootstrapUser("x", "", Role.VIEWER)).run(null))
				.isInstanceOf(IllegalStateException.class);
	}
}