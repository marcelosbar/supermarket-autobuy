package com.autobuy.web;

import com.autobuy.exception.AutoBuyException;
import com.autobuy.exception.CredentialException;
import com.autobuy.exception.DriverException;
import com.autobuy.exception.SettingsException;
import com.autobuy.exception.ShoppingListException;
import com.autobuy.web.dto.ErrorResponse;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(AutoBuyException.class)
	public ResponseEntity<ErrorResponse> handleAutoBuy(AutoBuyException ex) {
		log.warn("AutoBuy error: {}", ex.getMessage());
		return ResponseEntity.badRequest()
				.body(new ErrorResponse(ex.getMessage(), "AUTOBUY_ERROR", LocalDateTime.now(ZoneId.systemDefault())));
	}

	@ExceptionHandler(CredentialException.class)
	public ResponseEntity<ErrorResponse> handleCredential(CredentialException ex) {
		log.warn("Credential error: {}", ex.getMessage());
		return ResponseEntity.badRequest().body(
				new ErrorResponse(ex.getMessage(), "CREDENTIAL_ERROR", LocalDateTime.now(ZoneId.systemDefault())));
	}

	@ExceptionHandler(ShoppingListException.class)
	public ResponseEntity<ErrorResponse> handleShoppingList(ShoppingListException ex) {
		log.warn("Shopping list error: {}", ex.getMessage());
		return ResponseEntity.badRequest().body(
				new ErrorResponse(ex.getMessage(), "SHOPPING_LIST_ERROR", LocalDateTime.now(ZoneId.systemDefault())));
	}

	@ExceptionHandler(SettingsException.class)
	public ResponseEntity<ErrorResponse> handleSettings(SettingsException ex) {
		log.warn("Settings error: {}", ex.getMessage());
		return ResponseEntity.badRequest()
				.body(new ErrorResponse(ex.getMessage(), "SETTINGS_ERROR", LocalDateTime.now(ZoneId.systemDefault())));
	}

	@ExceptionHandler(DriverException.class)
	public ResponseEntity<ErrorResponse> handleDriver(DriverException ex) {
		log.error("Driver error: {}", ex.getMessage());
		return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
				.body(new ErrorResponse(ex.getMessage(), "DRIVER_ERROR", LocalDateTime.now(ZoneId.systemDefault())));
	}

	@ExceptionHandler(IllegalStateException.class)
	public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException ex) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(new ErrorResponse(ex.getMessage(), "STATE_ERROR", LocalDateTime.now(ZoneId.systemDefault())));
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ErrorResponse> handleIllegalArg(IllegalArgumentException ex) {
		return ResponseEntity.badRequest().body(
				new ErrorResponse(ex.getMessage(), "VALIDATION_ERROR", LocalDateTime.now(ZoneId.systemDefault())));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleGeneral(Exception ex) {
		log.error("Unexpected error", ex);
		return ResponseEntity.internalServerError()
				.body(new ErrorResponse(ex.getMessage(), "INTERNAL_ERROR", LocalDateTime.now(ZoneId.systemDefault())));
	}
}
