package com.autobuy.exception;

/** Exception thrown when browser driver operations fail. */
public class DriverException extends AutoBuyException {
	public DriverException(String message) {
		super(message);
	}

	public DriverException(String message, Throwable cause) {
		super(message, cause);
	}
}
