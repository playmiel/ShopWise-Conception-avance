package com.shopwise.app.security.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import com.shopwise.app.security.config.SecurityConfig;
import com.shopwise.app.security.config.SecurityProperties;
import com.shopwise.app.security.dto.TokenResponse;

/** Emet le jeton JWT d'un utilisateur authentifie : sujet, roles, emetteur, dates de validite. */
@Service
public class TokenService {

	private final JwtEncoder jwtEncoder;
	private final SecurityProperties properties;
	private final Clock clock;

	public TokenService(JwtEncoder jwtEncoder, SecurityProperties properties, Clock clock) {
		this.jwtEncoder = jwtEncoder;
		this.properties = properties;
		this.clock = clock;
	}

	public TokenResponse issue(Authentication authentication) {
		Instant now = clock.instant();
		List<String> roles = roles(authentication);
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer(properties.jwt().issuer())
				.subject(authentication.getName())
				.issuedAt(now)
				.expiresAt(now.plus(properties.jwt().expiration()))
				.claim(SecurityConfig.ROLES_CLAIM, roles)
				.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
		return new TokenResponse(token, "Bearer", properties.jwt().expiration().toSeconds(), authentication.getName(), roles);
	}

	/**
	 * Roles applicatifs, sans le prefixe ROLE_, tries. Seules les autorites ROLE_* sont retenues :
	 * Spring Security 7 ajoute aussi des autorites de facteur d'authentification (FACTOR_PASSWORD,
	 * FACTOR_BEARER) qui ne sont pas des roles et ne doivent pas finir dans le jeton.
	 */
	public static List<String> roles(Authentication authentication) {
		return authentication.getAuthorities().stream()
				.map(GrantedAuthority::getAuthority)
				.filter(authority -> authority.startsWith("ROLE_"))
				.map(authority -> authority.substring(5))
				.sorted()
				.toList();
	}
}