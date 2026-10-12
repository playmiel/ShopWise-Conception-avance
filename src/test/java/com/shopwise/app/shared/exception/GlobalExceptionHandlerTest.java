package com.shopwise.app.shared.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import com.shopwise.app.catalog.dto.request.CreateProductRequest;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

/**
 * Tests unitaires des cas du gestionnaire d'erreurs difficiles a provoquer via l'API :
 * conflit d'integrite, validation hors controleur, erreur inattendue.
 */
@DisplayName("Unitaire - GlobalExceptionHandler")
class GlobalExceptionHandlerTest {

	private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

	private static MockHttpServletRequest request(String method, String uri) {
		return new MockHttpServletRequest(method, uri);
	}

	@Test
	@DisplayName("ApiException : statut et code portes par l'exception")
	void apiException() {
		ResponseEntity<ApiError> response = handler.handleApiException(new NotFoundException("Sale not found"),
				request("GET", "/api/sales/7"));

		assertThat(response.getStatusCode().value()).isEqualTo(404);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().code()).isEqualTo("RESOURCE_NOT_FOUND");
		assertThat(response.getBody().message()).isEqualTo("Sale not found");
		assertThat(response.getBody().path()).isEqualTo("/api/sales/7");
	}

	@Test
	@DisplayName("ApiException sans message : message par defaut du code")
	void apiExceptionDefaultMessage() {
		ResponseEntity<ApiError> response = handler.handleApiException(new NotFoundException(),
				request("GET", "/api/products/1"));

		assertThat(response.getBody().message()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND.getDefaultMessage());
	}

	@Test
	@DisplayName("Violation d'integrite : 409 CONFLICT sans divulguer le detail SQL")
	void dataIntegrityViolation() {
		DataIntegrityViolationException ex = new DataIntegrityViolationException("could not execute statement",
				new SQLException("Referential integrity constraint violation: FK_SALE_LINE_PRODUCT ON PUBLIC.SALE_LINES"));

		ResponseEntity<ApiError> response = handler.handleDataIntegrity(ex, request("DELETE", "/api/products/1"));

		assertThat(response.getStatusCode().value()).isEqualTo(409);
		assertThat(response.getBody().code()).isEqualTo("CONFLICT");
		assertThat(response.getBody().message()).doesNotContain("FK_", "SALE_LINES", "PUBLIC");
	}

	@Test
	@DisplayName("ConstraintViolationException : 400 VALIDATION_ERROR avec les champs tries")
	void constraintViolation() {
		Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
		Set<ConstraintViolation<CreateProductRequest>> violations = validator.validate(new CreateProductRequest());

		ResponseEntity<ApiError> response = handler.handleConstraintViolation(
				new ConstraintViolationException(violations), request("POST", "/api/products"));

		assertThat(response.getStatusCode().value()).isEqualTo(400);
		assertThat(response.getBody().code()).isEqualTo("VALIDATION_ERROR");
		assertThat(response.getBody().errors()).extracting(ApiError.FieldViolation::field)
				.containsExactly("name", "price");
	}

	@Test
	@DisplayName("Erreur inattendue : 500 INTERNAL_ERROR avec un message generique")
	void unexpectedError() {
		ResponseEntity<ApiError> response = handler.handleUnexpected(
				new IllegalStateException("password=secret leaked in stack"), request("GET", "/api/products"));

		assertThat(response.getStatusCode().value()).isEqualTo(500);
		assertThat(response.getBody().code()).isEqualTo("INTERNAL_ERROR");
		assertThat(response.getBody().message()).isEqualTo("An unexpected error occurred")
				.doesNotContain("secret");
	}

	@Test
	@DisplayName("ErrorCode.fromStatus : correspondance statut HTTP vers code generique")
	void errorCodeFromStatus() {
		assertThat(ErrorCode.fromStatus(org.springframework.http.HttpStatus.FORBIDDEN)).isEqualTo(ErrorCode.FORBIDDEN);
		assertThat(ErrorCode.fromStatus(org.springframework.http.HttpStatus.UNAUTHORIZED)).isEqualTo(ErrorCode.UNAUTHORIZED);
		assertThat(ErrorCode.fromStatus(org.springframework.http.HttpStatus.NOT_ACCEPTABLE)).isEqualTo(ErrorCode.BAD_REQUEST);
		assertThat(ErrorCode.fromStatus(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE)).isEqualTo(ErrorCode.INTERNAL_ERROR);
	}
}