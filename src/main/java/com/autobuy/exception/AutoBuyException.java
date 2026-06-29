package com.autobuy.exception;

/**
 * Base unchecked exception for the Auto-Buy application.
 */
public class AutoBuyException extends RuntimeException {
	public AutoBuyException(String message) {
		super(message);
	}

	public AutoBuyException(String message, Throwable cause) {
		super(message, cause);
	}
}
