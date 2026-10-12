package com.shopwise.app.shared.exception;

import java.util.Comparator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.shopwise.app.shared.exception.ApiError.FieldViolation;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

/**
 * Point unique de traduction des exceptions en reponses HTTP au format {@link ApiError} (US 6).
 *
 * - Exceptions metier ({@link ApiException}) : statut et code portes par l'exception.
 * - Exceptions techniques de Spring MVC (JSON illisible, parametre mal type, methode non
 *   supportee, route inconnue...) : prises en charge par {@link ResponseEntityExceptionHandler}
 *   puis reformatees dans {@link #handleExceptionInternal}. Le code d'origine les laissait
 *   tomber dans le handler generique, d'ou des 500 au lieu de 400/404/405.
 * - Toute autre exception : 500 avec un message generique, sans fuite de detail interne.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	private static final Comparator<FieldViolation> BY_FIELD = Comparator.comparing(FieldViolation::field)
			.thenComparing(FieldViolation::message, Comparator.nullsFirst(Comparator.naturalOrder()));

	@ExceptionHandler(ApiException.class)
	public ResponseEntity<ApiError> handleApiException(ApiException ex, HttpServletRequest request) {
		ErrorCode code = ex.getErrorCode();
		log.debug("{} on {} {}: {}", code, request.getMethod(), request.getRequestURI(), ex.getMessage());
		return respond(ApiError.of(code, ex.getMessage(), request.getRequestURI()));
	}

	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex,
			HttpServletRequest request) {
		List<FieldViolation> violations = ex.getConstraintViolations().stream()
				.map(v -> new FieldViolation(v.getPropertyPath().toString(), v.getMessage()))
				.sorted(BY_FIELD)
				.toList();
		ErrorCode code = ErrorCode.VALIDATION_ERROR;
		return respond(ApiError.of(code.getStatus(), code, code.getDefaultMessage(), request.getRequestURI(), violations));
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
		// Le detail SQL est journalise mais jamais renvoye au client (il revele le schema de la base).
		log.warn("Data integrity violation on {} {}: {}", request.getMethod(), request.getRequestURI(),
				ex.getMostSpecificCause().getMessage());
		ErrorCode code = ErrorCode.CONFLICT;
		return respond(ApiError.of(code, code.getDefaultMessage(), request.getRequestURI()));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
		log.error("Unexpected error on {} {}", request.getMethod(), request.getRequestURI(), ex);
		ErrorCode code = ErrorCode.INTERNAL_ERROR;
		return respond(ApiError.of(code, code.getDefaultMessage(), request.getRequestURI()));
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		List<FieldViolation> violations = ex.getBindingResult().getAllErrors().stream()
				.map(GlobalExceptionHandler::toViolation)
				.sorted(BY_FIELD)
				.toList();
		ErrorCode code = ErrorCode.VALIDATION_ERROR;
		ApiError body = ApiError.of(status, code, code.getDefaultMessage(), path(request), violations);
		return super.handleExceptionInternal(ex, body, headers, status, request);
	}

	@Override
	protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
			HttpStatusCode statusCode, WebRequest request) {
		if (body instanceof ApiError) {
			return super.handleExceptionInternal(ex, body, headers, statusCode, request);
		}
		ErrorCode code = codeFor(ex, statusCode);
		String message = messageFor(ex, code, body, request);
		if (statusCode.is5xxServerError()) {
			log.error("Server error on {}", path(request), ex);
		} else {
			log.debug("{} on {}: {}", code, path(request), ex.getMessage());
		}
		ApiError apiError = ApiError.of(statusCode, code, message, path(request), List.of());
		return super.handleExceptionInternal(ex, apiError, headers, statusCode, request);
	}

	private static ErrorCode codeFor(Exception ex, HttpStatusCode status) {
		if (ex instanceof HttpMessageNotReadableException) {
			return ErrorCode.MALFORMED_REQUEST;
		}
		if (ex instanceof HandlerMethodValidationException) {
			return ErrorCode.VALIDATION_ERROR;
		}
		if (ex instanceof TypeMismatchException || ex instanceof MissingServletRequestParameterException) {
			return ErrorCode.INVALID_PARAMETER;
		}
		return ErrorCode.fromStatus(status);
	}

	private static String messageFor(Exception ex, ErrorCode code, Object body, WebRequest request) {
		if (ex instanceof HttpMessageNotReadableException) {
			return code.getDefaultMessage();
		}
		if (ex instanceof MethodArgumentTypeMismatchException mismatch) {
			return "Invalid value '%s' for parameter '%s'".formatted(mismatch.getValue(), mismatch.getName());
		}
		if (ex instanceof NoResourceFoundException && request instanceof ServletWebRequest swr) {
			return "No endpoint %s %s".formatted(swr.getRequest().getMethod(), swr.getRequest().getRequestURI());
		}
		if (body instanceof ProblemDetail problem && problem.getDetail() != null) {
			return problem.getDetail();
		}
		if (ex instanceof ErrorResponse errorResponse && errorResponse.getBody().getDetail() != null) {
			return errorResponse.getBody().getDetail();
		}
		return code.getDefaultMessage();
	}

	private static FieldViolation toViolation(ObjectError error) {
		String field = error instanceof FieldError fieldError ? fieldError.getField() : error.getObjectName();
		return new FieldViolation(field, error.getDefaultMessage());
	}

	private static String path(WebRequest request) {
		return request instanceof ServletWebRequest swr ? swr.getRequest().getRequestURI() : null;
	}

	private static ResponseEntity<ApiError> respond(ApiError error) {
		return ResponseEntity.status(error.status()).body(error);
	}
}