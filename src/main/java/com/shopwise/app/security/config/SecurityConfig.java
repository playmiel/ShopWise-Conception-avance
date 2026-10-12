package com.shopwise.app.security.config;

import java.util.Base64;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.shopwise.app.security.entity.Role;
import com.shopwise.app.security.web.RestAccessDeniedHandler;
import com.shopwise.app.security.web.RestAuthenticationEntryPoint;

/**
 * Securite de l'API (US 4 et 5).
 *
 * - Authentification sans etat par jeton JWT signe HMAC-SHA256 (en-tete Authorization: Bearer).
 * - Autorisation par role, centralisee dans la matrice ci-dessous (un seul endroit a auditer).
 * - CSRF desactive : aucune session ni cookie, le jeton est envoye explicitement par le client.
 * - 401 / 403 produits par le GlobalExceptionHandler, au meme format JSON que les autres erreurs.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfig {

	/** Claim du jeton portant les roles de l'utilisateur. */
	public static final String ROLES_CLAIM = "roles";

	private static final String ADMIN = Role.ADMIN.name();
	private static final String MERCHANT = Role.MERCHANT.name();
	private static final String VIEWER = Role.VIEWER.name();

	@Bean
	SecurityFilterChain apiSecurityFilterChain(HttpSecurity http, RestAuthenticationEntryPoint authenticationEntryPoint,
			RestAccessDeniedHandler accessDeniedHandler, JwtAuthenticationConverter jwtAuthenticationConverter)
			throws Exception {
		http
				.csrf(AbstractHttpConfigurer::disable)
				.httpBasic(AbstractHttpConfigurer::disable)
				.formLogin(AbstractHttpConfigurer::disable)
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
						// Public : obtention du jeton
						.requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
						.requestMatchers("/error").permitAll()
						// Lecture : tout utilisateur authentifie
						.requestMatchers(HttpMethod.GET, "/api/**").hasAnyRole(ADMIN, MERCHANT, VIEWER)
						// Ventes : commercant ou administrateur
						.requestMatchers(HttpMethod.POST, "/api/sales").hasAnyRole(ADMIN, MERCHANT)
						// Ecriture sur le catalogue : administrateur uniquement
						.requestMatchers("/api/products", "/api/products/**").hasRole(ADMIN)
						// Reentrainement du modele de recommandation : administrateur uniquement
						.requestMatchers(HttpMethod.POST, "/api/recommendations/**").hasRole(ADMIN)
						// Tout le reste exige au minimum d'etre authentifie
						.anyRequest().authenticated())
				.oauth2ResourceServer(oauth2 -> oauth2
						.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
						.authenticationEntryPoint(authenticationEntryPoint)
						.accessDeniedHandler(accessDeniedHandler))
				.exceptionHandling(exceptions -> exceptions
						.authenticationEntryPoint(authenticationEntryPoint)
						.accessDeniedHandler(accessDeniedHandler));
		return http.build();
	}

	/** Les roles du claim "roles" deviennent des autorites ROLE_xxx. */
	@Bean
	JwtAuthenticationConverter jwtAuthenticationConverter() {
		JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
		authorities.setAuthoritiesClaimName(ROLES_CLAIM);
		authorities.setAuthorityPrefix("ROLE_");
		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(authorities);
		return converter;
	}

	@Bean
	SecretKey jwtSigningKey(SecurityProperties properties) {
		byte[] key = Base64.getDecoder().decode(properties.jwt().secret());
		if (key.length < 32) {
			throw new IllegalStateException("shopwise.security.jwt.secret must be a Base64 key of at least 256 bits");
		}
		return new SecretKeySpec(key, "HmacSHA256");
	}

	@Bean
	JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
		return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSigningKey));
	}

	/** Verifie signature, algorithme, dates de validite et emetteur du jeton. */
	@Bean
	JwtDecoder jwtDecoder(SecretKey jwtSigningKey, SecurityProperties properties) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSigningKey)
				.macAlgorithm(MacAlgorithm.HS256)
				.build();
		decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.jwt().issuer()));
		return decoder;
	}

	/** BCrypt par defaut, avec prefixe d'algorithme pour pouvoir migrer plus tard. */
	@Bean
	PasswordEncoder passwordEncoder() {
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}

	@Bean
	AuthenticationManager authenticationManager(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
		DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
		provider.setPasswordEncoder(passwordEncoder);
		return new ProviderManager(provider);
	}
}