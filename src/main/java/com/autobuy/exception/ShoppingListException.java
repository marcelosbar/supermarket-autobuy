package com.autobuy.exception;

/** Exception thrown when shopping list loading fails. */
public class ShoppingListException extends AutoBuyException {
	public ShoppingListException(String message) {
		super(message);
	}

	public ShoppingListException(String message, Throwable cause) {
		super(message, cause);
	}
}
