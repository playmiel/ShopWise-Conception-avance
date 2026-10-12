package com.shopwise.app.security.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shopwise.app.security.dto.CurrentUserResponse;
import com.shopwise.app.security.dto.LoginRequest;
import com.shopwise.app.security.dto.TokenResponse;
import com.shopwise.app.security.service.TokenService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private static final Logger log = LoggerFactory.getLogger(AuthController.class);

	private final AuthenticationManager authenticationManager;
	private final TokenService tokenService;

	public AuthController(AuthenticationManager authenticationManager, TokenService tokenService) {
		this.authenticationManager = authenticationManager;
		this.tokenService = tokenService;
	}

	/** Echange identifiant + mot de passe contre un jeton JWT ; 401 si les identifiants sont faux. */
	@PostMapping("/login")
	public TokenResponse login(@Valid @RequestBody LoginRequest request) {
		String username = request.username().replaceAll("[\\r\\n]", "_");
		try {
			Authentication authentication = authenticationManager.authenticate(
					UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password()));
			log.info("User '{}' authenticated", username);
			return tokenService.issue(authentication);
		} catch (AuthenticationException ex) {
			log.warn("Failed login attempt for user '{}'", username);
			throw ex;
		}
	}

	/** Utilisateur correspondant au jeton fourni. */
	@GetMapping("/me")
	public CurrentUserResponse me(Authentication authentication) {
		return new CurrentUserResponse(authentication.getName(), TokenService.roles(authentication));
	}
}