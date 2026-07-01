package com.autobuy.web;

import com.autobuy.model.ProductMapping;
import com.autobuy.model.ShoppingItem;
import com.autobuy.provider.CredentialProvider;
import com.autobuy.provider.ShoppingListProvider;
import com.autobuy.web.dto.AutoBuyStatusResponse;
import com.autobuy.web.dto.CredentialsRequest;
import com.autobuy.web.dto.ResolveRequest;
import com.autobuy.web.dto.RunRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST Controller exposing endpoints to manage shopping list, mappings,
 * credentials, and autobuy execution.
 *
 * <p>
 * Uses constructor injection to adhere to the Dependency Inversion Principle
 * (DIP).
 */
@RestController
@RequestMapping("/api")
public class WebApiController {

	private static final Logger log = LoggerFactory.getLogger(WebApiController.class);

	private final AutoBuyWebService autoBuyWebService;
	private final com.autobuy.service.ProductService productService;
	private final CredentialProvider credentialProvider;
	private final ShoppingListProvider shoppingListProvider;
	private final ObjectMapper objectMapper;

	private static final String DEFAULT_LIST_PATH = "shopping-list.json";
	private static final String SUCCESS_KEY = "success";
	private static final String MESSAGE_KEY = "message";

	public WebApiController(AutoBuyWebService autoBuyWebService, com.autobuy.service.ProductService productService,
			CredentialProvider credentialProvider, ShoppingListProvider shoppingListProvider,
			ObjectMapper objectMapper) {
		this.autoBuyWebService = autoBuyWebService;
		this.productService = productService;
		this.credentialProvider = credentialProvider;
		this.shoppingListProvider = shoppingListProvider;
		this.objectMapper = objectMapper;
	}

	// 1. Shopping List Endpoints

	@GetMapping("/shopping-list")
	public ResponseEntity<List<ShoppingItem>> getShoppingList() {
		List<ShoppingItem> items = shoppingListProvider.getShoppingList(DEFAULT_LIST_PATH);
		return ResponseEntity.ok(items);
	}

	@PostMapping("/shopping-list")
	public ResponseEntity<List<ShoppingItem>> saveShoppingList(@RequestBody List<ShoppingItem> items) {
		try {
			objectMapper.writeValue(new File(DEFAULT_LIST_PATH), items);
			log.info("Saved updated shopping list to {}", DEFAULT_LIST_PATH);
			return ResponseEntity.ok(items);
		} catch (Exception e) {
			log.error("Failed to write shopping list to file", e);
			return ResponseEntity.internalServerError().build();
		}
	}

	// 2. Mapping Endpoints

	@GetMapping("/mappings")
	public ResponseEntity<List<ProductMapping>> getMappings() {
		return ResponseEntity.ok(productService.findAllMappings());
	}

	@DeleteMapping("/mappings/{id}")
	public ResponseEntity<Void> deleteMapping(@PathVariable Long id) {
		if (productService.findMappingById(id).isPresent()) {
			productService.deleteMapping(id);
			log.info("Deleted product mapping ID {}", id);
			return ResponseEntity.noContent().build();
		}
		return ResponseEntity.notFound().build();
	}

	// 3. Credentials Endpoints

	@GetMapping("/credentials")
	public ResponseEntity<Map<String, Object>> getCredentialsStatus(
			@RequestParam(defaultValue = "CONTINENTE") String supermarket) {
		String username = credentialProvider.getUsername(supermarket);
		String password = credentialProvider.getPassword(supermarket);

		Map<String, Object> status = new HashMap<>();
		status.put("supermarket", supermarket.toUpperCase());
		status.put("hasUsername", username != null && !username.isBlank());
		status.put("hasPassword", password != null && !password.isBlank());
		status.put("username", username != null ? username : "");
		return ResponseEntity.ok(status);
	}

	@PostMapping("/credentials")
	public ResponseEntity<Map<String, Object>> saveCredentials(@RequestBody CredentialsRequest request) {
		try {
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

	// 4. Execution Endpoints

	@PostMapping("/autobuy/run")
	public ResponseEntity<Map<String, Object>> runAutoBuy(@RequestBody RunRequest request) {
		try {
			boolean headless = false; // Always run headfully to allow visual mapping/checkout review
			String supermarket = request.supermarket() != null ? request.supermarket() : "CONTINENTE";

			autoBuyWebService.startAutoBuy(DEFAULT_LIST_PATH, supermarket, headless);

			Map<String, Object> response = new HashMap<>();
			response.put(SUCCESS_KEY, true);
			response.put(MESSAGE_KEY, "Auto-Buy started successfully.");
			return ResponseEntity.ok(response);
		} catch (IllegalStateException e) {
			Map<String, Object> response = new HashMap<>();
			response.put(SUCCESS_KEY, false);
			response.put(MESSAGE_KEY, e.getMessage());
			return ResponseEntity.badRequest().body(response);
		}
	}

	@GetMapping("/autobuy/status")
	public ResponseEntity<AutoBuyStatusResponse> getStatus() {
		return ResponseEntity.ok(autoBuyWebService.getStatus());
	}

	@PostMapping("/autobuy/resolve")
	public ResponseEntity<Map<String, Object>> resolveMapping(@RequestBody ResolveRequest request) {
		try {
			autoBuyWebService.resolveMapping(request.externalId());
			Map<String, Object> response = new HashMap<>();
			response.put(SUCCESS_KEY, true);
			return ResponseEntity.ok(response);
		} catch (Exception e) {
			Map<String, Object> response = new HashMap<>();
			response.put(SUCCESS_KEY, false);
			response.put(MESSAGE_KEY, e.getMessage());
			return ResponseEntity.badRequest().body(response);
		}
	}

	@PostMapping("/autobuy/complete")
	public ResponseEntity<Map<String, Object>> completeRun() {
		try {
			autoBuyWebService.completeRun();
			Map<String, Object> response = new HashMap<>();
			response.put(SUCCESS_KEY, true);
			return ResponseEntity.ok(response);
		} catch (Exception e) {
			Map<String, Object> response = new HashMap<>();
			response.put(SUCCESS_KEY, false);
			response.put(MESSAGE_KEY, e.getMessage());
			return ResponseEntity.badRequest().body(response);
		}
	}

	@PostMapping("/autobuy/cancel")
	public ResponseEntity<Map<String, Object>> cancelRun() {
		try {
			autoBuyWebService.cancel();
			Map<String, Object> response = new HashMap<>();
			response.put(SUCCESS_KEY, true);
			return ResponseEntity.ok(response);
		} catch (Exception e) {
			Map<String, Object> response = new HashMap<>();
			response.put(SUCCESS_KEY, false);
			response.put(MESSAGE_KEY, e.getMessage());
			return ResponseEntity.badRequest().body(response);
		}
	}

}
