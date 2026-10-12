package com.shopwise.app.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

/**
 * Codes d'erreur exposes par l'API (champ "code" de {@link ApiError}).
 *
 * Le code est un contrat stable pour les clients de l'API : ils peuvent s'y fier pour reagir
 * a une erreur, alors que le message, destine a un humain, peut evoluer librement.
 */
public enum ErrorCode {

	VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Request validation failed"),
	MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "Malformed JSON request body"),
	INVALID_PARAMETER(HttpStatus.BAD_REQUEST, "Invalid request parameter"),
	BAD_REQUEST(HttpStatus.BAD_REQUEST, "Bad request"),
	UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Authentication is required to access this resource"),
	FORBIDDEN(HttpStatus.FORBIDDEN, "You are not allowed to perform this operation"),
	RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found"),
	METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "HTTP method not allowed on this resource"),
	CONFLICT(HttpStatus.CONFLICT, "The operation conflicts with existing data"),
	UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported media type"),
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");

	private final HttpStatus status;
	private final String defaultMessage;

	ErrorCode(HttpStatus status, String defaultMessage) {
		this.status = status;
		this.defaultMessage = defaultMessage;
	}

	public HttpStatus getStatus() {
		return status;
	}

	public String getDefaultMessage() {
		return defaultMessage;
	}

	/** Code generique correspondant a un statut HTTP, utilise quand aucun code plus precis n'existe. */
	public static ErrorCode fromStatus(HttpStatusCode status) {
		return switch (status.value()) {
			case 400 -> BAD_REQUEST;
			case 401 -> UNAUTHORIZED;
			case 403 -> FORBIDDEN;
			case 404 -> RESOURCE_NOT_FOUND;
			case 405 -> METHOD_NOT_ALLOWED;
			case 409 -> CONFLICT;
			case 415 -> UNSUPPORTED_MEDIA_TYPE;
			default -> status.is4xxClientError() ? BAD_REQUEST : INTERNAL_ERROR;
		};
	}
}