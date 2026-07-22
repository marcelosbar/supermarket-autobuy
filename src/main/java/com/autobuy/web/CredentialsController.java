package com.autobuy.web;

import com.autobuy.provider.CredentialProvider;
import com.autobuy.web.dto.ActionResponse;
import com.autobuy.web.dto.CredentialStatusResponse;
import com.autobuy.web.dto.CredentialsRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller exposing credential management endpoints.
 */
@RestController
@RequestMapping("/credentials")
public class CredentialsController {

	private static final Logger log = LoggerFactory.getLogger(CredentialsController.class);

	private final CredentialProvider credentialProvider;
	private static final String DEFAULT_SUPERMARKET = "CONTINENTE";

	public CredentialsController(CredentialProvider credentialProvider) {
		this.credentialProvider = credentialProvider;
	}

	@GetMapping
	public ResponseEntity<CredentialStatusResponse> getCredentialsStatus(
			@RequestParam(defaultValue = DEFAULT_SUPERMARKET) String supermarket) {
		String username = credentialProvider.getUsername(supermarket);
		String password = credentialProvider.getPassword(supermarket);

		boolean hasUsername = username != null && !username.isBlank();
		boolean hasPassword = password != null && !password.isBlank();
		String resolvedUsername = username != null ? username : "";

		return ResponseEntity.ok(
				new CredentialStatusResponse(supermarket.toUpperCase(), hasUsername, hasPassword, resolvedUsername));
	}

	@PostMapping
	public ResponseEntity<ActionResponse> saveCredentials(@RequestBody CredentialsRequest request) {
		String existingUsername = credentialProvider.getUsername(request.supermarket());
		String existingPassword = credentialProvider.getPassword(request.supermarket());
		boolean hasExisting = existingUsername != null && !existingUsername.isBlank() && existingPassword != null
				&& !existingPassword.isBlank();

		if (hasExisting && request.username() != null && request.username().trim().equals(existingUsername.trim())
				&& (request.password() == null || request.password().isBlank())) {
			log.info("Credentials unchanged (password omitted), skipping credentials update.");
			return ResponseEntity.ok(new ActionResponse(true, "Credentials unchanged."));
		}

		credentialProvider.saveCredentials(request.supermarket(), request.username(), request.password());
		return ResponseEntity.ok(new ActionResponse(true, "Credentials saved successfully."));
	}
}
