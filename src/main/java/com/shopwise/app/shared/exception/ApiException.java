package com.shopwise.app.shared.exception;

/**
 * Exception metier de base : porte un {@link ErrorCode} qui determine le statut HTTP et le code
 * renvoyes au client. Les modules (catalogue, ventes, ...) l'etendent plutot que de manipuler
 * des statuts HTTP, ce qui garde la couche service independante du web.
 */
public class ApiException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	private final ErrorCode errorCode;

	public ApiException(ErrorCode errorCode, String message) {
		this(errorCode, message, null);
	}

	public ApiException(ErrorCode errorCode, String message, Throwable cause) {
		super(message != null ? message : errorCode.getDefaultMessage(), cause);
		this.errorCode = errorCode;
	}

	public ErrorCode getErrorCode() {
		return errorCode;
	}
}