package com.autobuy.exception;

/**
 * Exception thrown when credential operations fail.
 */
public class CredentialException extends AutoBuyException {
	public CredentialException(String message) {
		super(message);
	}

	public CredentialException(String message, Throwable cause) {
		super(message, cause);
	}
}
