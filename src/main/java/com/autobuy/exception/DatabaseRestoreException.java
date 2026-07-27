package com.autobuy.exception;

/**
 * Exception thrown when database backup restore operations fail or backup files
 * are invalid.
 */
public class DatabaseRestoreException extends AutoBuyException {
	public DatabaseRestoreException(String message) {
		super(message);
	}

	public DatabaseRestoreException(String message, Throwable cause) {
		super(message, cause);
	}
}
