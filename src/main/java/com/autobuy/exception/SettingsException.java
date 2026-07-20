package com.autobuy.exception;

/**
 * Exception thrown when configuration or settings operations fail.
 */
public class SettingsException extends AutoBuyException {
	public SettingsException(String message) {
		super(message);
	}

	public SettingsException(String message, Throwable cause) {
		super(message, cause);
	}
}
