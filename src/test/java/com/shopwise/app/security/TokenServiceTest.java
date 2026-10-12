package com.shopwise.app.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.shopwise.app.security.config.SecurityProperties;
import com.shopwise.app.security.dto.TokenResponse;
import com.shopwise.app.security.service.TokenService;

@DisplayName("Unitaire - TokenService")
class TokenServiceTest {

	private static final byte[] KEY = Base64.getDecoder().decode("q2Xv9Q0mR0n8yQxk0VtZ3m7m3k2e1JcV5w8hQyT4bWc=");
	private static final Instant NOW = Instant.parse("2026-10-12T10:00:00Z");

	@Test
	@DisplayName("le jeton porte sujet, roles, emetteur et une expiration a now + duree configuree")
	void issueToken() {
		SecretKey key = new SecretKeySpec(KEY, "HmacSHA256");
		SecurityProperties properties = new SecurityProperties(
				new SecurityProperties.Jwt("unused", Duration.ofMinutes(30), "shopwise"), List.of());
		TokenService service = new TokenService(new NimbusJwtEncoder(new ImmutableSecret<>(key)), properties,
				Clock.fixed(NOW, ZoneOffset.UTC));
		var authentication = UsernamePasswordAuthenticationToken.authenticated("marie", null,
				List.of(new SimpleGrantedAuthority("ROLE_MERCHANT")));

		TokenResponse response = service.issue(authentication);

		assertThat(response.tokenType()).isEqualTo("Bearer");
		assertThat(response.expiresIn()).isEqualTo(1800);
		assertThat(response.roles()).containsExactly("MERCHANT");

		// Decodage sans validation temporelle (horloge fixe dans le passe) : on verifie le contenu signe
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
		decoder.setJwtValidator(jwt -> OAuth2TokenValidatorResult.success());
		Jwt jwt = decoder.decode(response.accessToken());
		assertThat(jwt.getSubject()).isEqualTo("marie");
		assertThat(jwt.getClaimAsString("iss")).isEqualTo("shopwise");
		assertThat(jwt.getClaimAsStringList("roles")).containsExactly("MERCHANT");
		assertThat(jwt.getIssuedAt()).isEqualTo(NOW);
		assertThat(jwt.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(30)));
	}
}