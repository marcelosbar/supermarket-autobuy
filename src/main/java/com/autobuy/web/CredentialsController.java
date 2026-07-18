package com.autobuy.web;

import com.autobuy.provider.CredentialProvider;
import com.autobuy.web.dto.CredentialsRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller exposing credential management endpoints.
 */
@RestController
@RequestMapping("/credentials")
public class CredentialsController {

	private static final Logger log = LoggerFactory.getLogger(CredentialsController.class);

	private final CredentialProvider credentialProvider;
	private static final String DEFAULT_SUPERMARKET = "CONTINENTE";
	private static final String SUCCESS_KEY = "success";
	private static final String MESSAGE_KEY = "message";

	public CredentialsController(CredentialProvider credentialProvider) {
		this.credentialProvider = credentialProvider;
	}

	@GetMapping
	public ResponseEntity<Map<String, Object>> getCredentialsStatus(
			@RequestParam(defaultValue = DEFAULT_SUPERMARKET) String supermarket) {
		String username = credentialProvider.getUsername(supermarket);
		String password = credentialProvider.getPassword(supermarket);

		Map<String, Object> status = new HashMap<>();
		status.put("supermarket", supermarket.toUpperCase());
		status.put("hasUsername", username != null && !username.isBlank());
		status.put("hasPassword", password != null && !password.isBlank());
		status.put("username", username != null ? username : "");
		return ResponseEntity.ok(status);
	}

	@PostMapping
	public ResponseEntity<Map<String, Object>> saveCredentials(@RequestBody CredentialsRequest request) {
		try {
			String existingUsername = credentialProvider.getUsername(request.supermarket());
			String existingPassword = credentialProvider.getPassword(request.supermarket());
			boolean hasExisting = existingUsername != null && !existingUsername.isBlank() && existingPassword != null
					&& !existingPassword.isBlank();

			if (hasExisting && request.username() != null && request.username().trim().equals(existingUsername.trim())
					&& (request.password() == null || request.password().isBlank())) {
				log.info("Credentials unchanged (password omitted), skipping credentials update.");
				Map<String, Object> response = new HashMap<>();
				response.put(SUCCESS_KEY, true);
				response.put(MESSAGE_KEY, "Credentials unchanged.");
				return ResponseEntity.ok(response);
			}

			credentialProvider.saveCredentials(request.supermarket(), request.username(), request.password());
			Map<String, Object> response = new HashMap<>();
			response.put(SUCCESS_KEY, true);
			response.put(MESSAGE_KEY, "Credentials saved successfully.");
			return ResponseEntity.ok(response);
		} catch (UnsupportedOperationException e) {
			log.error("SOLID Exception: CredentialProvider does not support saving credentials dynamically.", e);
			Map<String, Object> response = new HashMap<>();
			response.put(SUCCESS_KEY, false);
			response.put(MESSAGE_KEY, "Database/properties credentials saving not supported in this profile.");
			return ResponseEntity.internalServerError().body(response);
		} catch (com.autobuy.exception.CredentialException e) {
			log.error("Failed to save credentials", e);
			Map<String, Object> response = new HashMap<>();
			response.put(SUCCESS_KEY, false);
			response.put(MESSAGE_KEY, "Failed to save credentials: " + e.getMessage());
			return ResponseEntity.internalServerError().body(response);
		}
	}
}
