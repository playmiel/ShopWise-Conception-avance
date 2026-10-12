package com.shopwise.app.shared.exception;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatusCode;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Format JSON unique de toutes les reponses d'erreur de l'API (US 6).
 *
 * <pre>
 * {
 *   "status": 404,
 *   "code": "RESOURCE_NOT_FOUND",
 *   "message": "Product not found",
 *   "path": "/api/products/42",
 *   "timestamp": "2026-10-12T09:15:30.123Z",
 *   "errors": [ { "field": "price", "message": "must be greater than 0" } ]
 * }
 * </pre>
 *
 * "errors" n'apparait que pour les erreurs de validation (liste des champs en faute).
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(
		int status,
		String code,
		String message,
		String path,
		Instant timestamp,
		List<FieldViolation> errors) {

	/** Erreur de validation portant sur un champ de la requete. */
	public record FieldViolation(String field, String message) {
	}

	public static ApiError of(HttpStatusCode status, ErrorCode code, String message, String path,
			List<FieldViolation> errors) {
		return new ApiError(status.value(), code.name(), message, path, Instant.now(), List.copyOf(errors));
	}

	public static ApiError of(ErrorCode code, String message, String path) {
		return of(code.getStatus(), code, message, path, List.of());
	}
}