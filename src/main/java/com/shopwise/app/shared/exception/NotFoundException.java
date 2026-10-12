package com.shopwise.app.shared.exception;

/** Ressource demandee introuvable : traduite en HTTP 404 / RESOURCE_NOT_FOUND. */
public class NotFoundException extends ApiException {

	private static final long serialVersionUID = 1L;

	public NotFoundException() {
		super(ErrorCode.RESOURCE_NOT_FOUND, null);
	}

	public NotFoundException(String message) {
		super(ErrorCode.RESOURCE_NOT_FOUND, message);
	}

	public NotFoundException(String message, Throwable cause) {
		super(ErrorCode.RESOURCE_NOT_FOUND, message, cause);
	}
}