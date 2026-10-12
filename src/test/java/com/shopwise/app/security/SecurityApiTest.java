package com.shopwise.app.security;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

/**
 * Tests de bout en bout de la securite avec de VRAIS jetons : login, utilisation du jeton,
 * 401 (absent, invalide, expire, falsifie) et 403, au format d'erreur normalise.
 * Comptes : admin/admin123, merchant/merchant123, viewer/viewer123 (shopwise.security.bootstrap-users).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Tag("api")
@DisplayName("API - securite (US 4 et 5)")
class SecurityApiTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtEncoder jwtEncoder;

	private String login(String username, String password) throws Exception {
		String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password)))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.accessToken");
	}

	private static String bearer(String token) {
		return "Bearer " + token;
	}

	private String forgeToken(JwtEncoder encoder, String issuer, Instant issuedAt, Instant expiresAt) {
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer(issuer).subject("admin").issuedAt(issuedAt).expiresAt(expiresAt)
				.claim("roles", List.of("ADMIN"))
				.build();
		return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
				.getTokenValue();
	}

	@Nested
	@DisplayName("POST /api/auth/login")
	class Login {

		@Test
		@DisplayName("identifiants valides : jeton Bearer, duree de validite et roles")
		void validCredentials() throws Exception {
			mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
					.content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.accessToken", notNullValue()))
					.andExpect(jsonPath("$.tokenType").value("Bearer"))
					.andExpect(jsonPath("$.expiresIn").value(3600))
					.andExpect(jsonPath("$.username").value("admin"))
					.andExpect(jsonPath("$.roles[0]").value("ADMIN"));
		}

		@Test
		@DisplayName("mot de passe faux : 401 sans indiquer si le compte existe")
		void wrongPassword() throws Exception {
			mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
					.content("{\"username\":\"admin\",\"password\":\"wrong\"}"))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
					.andExpect(jsonPath("$.message").value("Invalid username or password"));
		}

		@Test
		@DisplayName("compte inconnu : meme reponse que mot de passe faux (pas d'enumeration)")
		void unknownUser() throws Exception {
			mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
					.content("{\"username\":\"nobody\",\"password\":\"whatever\"}"))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.message").value("Invalid username or password"));
		}

		@Test
		@DisplayName("champs manquants : 400 VALIDATION_ERROR")
		void missingFields() throws Exception {
			mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"\"}"))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		}
	}

	@Nested
	@DisplayName("Jeton (US 5)")
	class Token {

		@Test
		@DisplayName("le jeton obtenu donne acces aux operations protegees")
		void tokenGrantsAccess() throws Exception {
			String token = login("viewer", "viewer123");

			mockMvc.perform(get("/api/products").header(HttpHeaders.AUTHORIZATION, bearer(token)))
					.andExpect(status().isOk());
		}

		@Test
		@DisplayName("GET /api/auth/me renvoie l'utilisateur du jeton")
		void me() throws Exception {
			String token = login("merchant", "merchant123");

			mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.username").value("merchant"))
					.andExpect(jsonPath("$.roles[0]").value("MERCHANT"));
		}

		@Test
		@DisplayName("sans jeton : 401, en-tete WWW-Authenticate et format normalise")
		void missingToken() throws Exception {
			mockMvc.perform(get("/api/products"))
					.andExpect(status().isUnauthorized())
					.andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, containsString("Bearer")))
					.andExpect(jsonPath("$.status").value(401))
					.andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
					.andExpect(jsonPath("$.message", notNullValue()))
					.andExpect(jsonPath("$.path").value("/api/products"))
					.andExpect(jsonPath("$.timestamp", notNullValue()));
		}

		@Test
		@DisplayName("jeton mal forme : 401 Invalid or expired token")
		void malformedToken() throws Exception {
			mockMvc.perform(get("/api/products").header(HttpHeaders.AUTHORIZATION, "Bearer not.a.jwt"))
					.andExpect(status().isUnauthorized())
					.andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, containsString("invalid_token")))
					.andExpect(jsonPath("$.message").value("Invalid or expired token"));
		}

		@Test
		@DisplayName("jeton expire : 401")
		void expiredToken() throws Exception {
			Instant now = Instant.now();
			String expired = forgeToken(jwtEncoder, "shopwise", now.minus(2, ChronoUnit.HOURS), now.minus(1, ChronoUnit.HOURS));

			mockMvc.perform(get("/api/products").header(HttpHeaders.AUTHORIZATION, bearer(expired)))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.message").value("Invalid or expired token"));
		}

		@Test
		@DisplayName("jeton signe avec une autre cle (falsifie) : 401")
		void foreignSignature() throws Exception {
			SecretKeySpec otherKey = new SecretKeySpec("another-secret-key-of-at-least-32-bytes!!".getBytes(), "HmacSHA256");
			JwtEncoder attacker = new NimbusJwtEncoder(new ImmutableSecret<>(otherKey));
			Instant now = Instant.now();
			String forged = forgeToken(attacker, "shopwise", now, now.plus(1, ChronoUnit.HOURS));

			mockMvc.perform(get("/api/products").header(HttpHeaders.AUTHORIZATION, bearer(forged)))
					.andExpect(status().isUnauthorized());
		}

		@Test
		@DisplayName("jeton d'un autre emetteur : 401")
		void wrongIssuer() throws Exception {
			Instant now = Instant.now();
			String token = forgeToken(jwtEncoder, "someone-else", now, now.plus(1, ChronoUnit.HOURS));

			mockMvc.perform(get("/api/products").header(HttpHeaders.AUTHORIZATION, bearer(token)))
					.andExpect(status().isUnauthorized());
		}
	}

	@Nested
	@DisplayName("Autorisations (US 4)")
	class Authorization {

		@Test
		@DisplayName("role insuffisant : 403 avec un message explicite")
		void forbiddenWithExplicitMessage() throws Exception {
			String token = login("viewer", "viewer123");

			mockMvc.perform(post("/api/products").header(HttpHeaders.AUTHORIZATION, bearer(token))
					.contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Pirate\",\"price\":1.00}"))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.status").value(403))
					.andExpect(jsonPath("$.code").value("FORBIDDEN"))
					.andExpect(jsonPath("$.message").value("Access denied: role VIEWER is not allowed to POST /api/products"))
					.andExpect(jsonPath("$.path").value("/api/products"));
		}

		@Test
		@DisplayName("vente enregistree par un commercant : l'auteur est trace")
		void saleIsTraced() throws Exception {
			String token = login("merchant", "merchant123");

			mockMvc.perform(post("/api/sales").header(HttpHeaders.AUTHORIZATION, bearer(token))
					.contentType(MediaType.APPLICATION_JSON).content("{\"lines\":[{\"productId\":1,\"quantity\":1}]}"))
					.andExpect(status().isCreated())
					.andExpect(jsonPath("$.createdBy").value("merchant"));
		}
	}
}