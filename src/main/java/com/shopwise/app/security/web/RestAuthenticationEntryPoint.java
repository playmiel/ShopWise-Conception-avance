package com.shopwise.app.security.web;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 401 : jeton absent, invalide ou expire. Les filtres de securite s'executent avant Spring MVC,
 * hors de portee du @RestControllerAdvice : on lui delegue explicitement l'exception pour
 * obtenir le meme format JSON que toutes les autres erreurs.
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

	private final HandlerExceptionResolver resolver;

	public RestAuthenticationEntryPoint(@Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
		this.resolver = resolver;
	}

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex) {
		resolver.resolveException(request, response, null, ex);
	}
}