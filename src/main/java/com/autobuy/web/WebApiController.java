package com.autobuy.web;

import com.autobuy.provider.SettingsProvider;
import com.autobuy.service.DatabaseBackupService;
import com.autobuy.service.ShutdownService;
import com.autobuy.model.ProductMapping;
import com.autobuy.model.ShoppingItem;
import com.autobuy.provider.CredentialProvider;
import com.autobuy.provider.ShoppingListProvider;
import com.autobuy.web.dto.AutoBuyStatusResponse;
import com.autobuy.web.dto.CredentialsRequest;
import com.autobuy.web.dto.ResolveRequest;
import com.autobuy.web.dto.RunRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.awt.GraphicsEnvironment;
import javax.swing.JFileChooser;
import java.util.concurrent.atomic.AtomicReference;
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
	private final SettingsProvider settingsProvider;
	private final ShoppingListProvider shoppingListProvider;
	private final ShutdownService shutdownService;
	private final DatabaseBackupService databaseBackupService;

	private static final String DEFAULT_LIST_PATH = "shopping-list.json";
	private static final String SUCCESS_KEY = "success";
	private static final String MESSAGE_KEY = "message";
	private static final String BACKUP_DIR_KEY = "backupDir";
	private static final String DEFAULT_SUPERMARKET = "CONTINENTE";

	public WebApiController(AutoBuyWebService autoBuyWebService, com.autobuy.service.ProductService productService,
			CredentialProvider credentialProvider, SettingsProvider settingsProvider,
			ShoppingListProvider shoppingListProvider, ShutdownService shutdownService,
			@org.springframework.beans.factory.annotation.Autowired(required = false) DatabaseBackupService databaseBackupService) {
		this.autoBuyWebService = autoBuyWebService;
		this.productService = productService;
		this.credentialProvider = credentialProvider;
		this.settingsProvider = settingsProvider;
		this.shoppingListProvider = shoppingListProvider;
		this.shutdownService = shutdownService;
		this.databaseBackupService = databaseBackupService;
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
			shoppingListProvider.saveShoppingList(DEFAULT_LIST_PATH, items);
			return ResponseEntity.ok(items);
		} catch (Exception e) {
			log.error("Failed to save shopping list via provider", e);
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

	@PostMapping("/credentials")
	public ResponseEntity<Map<String, Object>> saveCredentials(@RequestBody CredentialsRequest request) {
		try {
			// Double-safeguard: If username is unchanged and password is blank/omitted,
			// skip saving
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

	// 4. Execution Endpoints

	@PostMapping("/autobuy/run")
	public ResponseEntity<Map<String, Object>> runAutoBuy(@RequestBody RunRequest request) {
		try {
			boolean headless = false; // Always run headfully to allow visual mapping/checkout review
			String supermarket = request.supermarket() != null ? request.supermarket() : DEFAULT_SUPERMARKET;

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

	@GetMapping("/config/backup-dir")
	public ResponseEntity<Map<String, Object>> getBackupDir() {
		Map<String, Object> response = new HashMap<>();
		String dir = (settingsProvider != null) ? settingsProvider.getBackupDir() : null;
		response.put(BACKUP_DIR_KEY, dir != null ? dir : "");
		return ResponseEntity.ok(response);
	}

	@PostMapping("/config/backup-dir")
	public ResponseEntity<Map<String, Object>> saveBackupDir(@RequestBody Map<String, String> request) {
		try {
			String path = request.get(BACKUP_DIR_KEY);
			String resolvedPath = (path == null || path.trim().isEmpty()) ? null : path.trim();

			if (settingsProvider != null) {
				settingsProvider.saveBackupDir(resolvedPath);
			}

			if (databaseBackupService != null) {
				databaseBackupService.setBackupDir(resolvedPath);
			}

			Map<String, Object> response = new HashMap<>();
			response.put(SUCCESS_KEY, true);
			return ResponseEntity.ok(response);
		} catch (Exception e) {
			log.error("Failed to save backup directory", e);
			Map<String, Object> response = new HashMap<>();
			response.put(SUCCESS_KEY, false);
			response.put(MESSAGE_KEY, "Failed to save backup directory: " + e.getMessage());
			return ResponseEntity.internalServerError().body(response);
		}
	}

	@GetMapping("/autobuy/backup-status")
	public ResponseEntity<Map<String, Object>> getBackupStatus() {
		Map<String, Object> status = new HashMap<>();
		String currentDir = (databaseBackupService != null) ? databaseBackupService.getBackupDir() : null;
		boolean isConfigured = currentDir != null && !currentDir.trim().isEmpty();
		status.put(BACKUP_DIR_KEY, currentDir != null ? currentDir : "");
		status.put("isConfigured", isConfigured);
		return ResponseEntity.ok(status);
	}

	@PostMapping("/config/select-native-dir")
	public ResponseEntity<Map<String, Object>> selectNativeDirectory() {
		if (GraphicsEnvironment.isHeadless()) {
			log.warn("Cannot open native folder picker: Graphics environment is headless.");
			Map<String, Object> response = new HashMap<>();
			response.put(SUCCESS_KEY, false);
			response.put(MESSAGE_KEY, "Cannot open native folder picker: Graphics environment is headless.");
			return ResponseEntity.badRequest().body(response);
		}

		log.info("selectNativeDirectory endpoint called.");
		AtomicReference<String> selectedPath = new AtomicReference<>(null);
		try {
			log.info("Setting system look and feel...");
			setSystemLookAndFeel();

			log.info("Instantiating JFileChooser...");
			JFileChooser chooser = new JFileChooser();
			chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
			chooser.setDialogTitle("Select Database Backup Directory");
			chooser.setCurrentDirectory(new File(System.getProperty("user.home")));

			log.info("Showing native open dialog...");
			int result = chooser.showOpenDialog(null);
			log.info("Native open dialog closed with result: {}", result);

			if (result == JFileChooser.APPROVE_OPTION) {
				selectedPath.set(chooser.getSelectedFile().getAbsolutePath().replace('\\', '/'));
				log.info("Directory selected: {}", selectedPath.get());
			}

			Map<String, Object> response = new HashMap<>();
			if (selectedPath.get() != null) {
				response.put(SUCCESS_KEY, true);
				response.put("path", selectedPath.get());
			} else {
				response.put(SUCCESS_KEY, false);
				response.put(MESSAGE_KEY, "Selection cancelled");
			}
			return ResponseEntity.ok(response);
		} catch (Exception e) {
			log.error("Failed to open native directory chooser", e);
			Map<String, Object> response = new HashMap<>();
			response.put(SUCCESS_KEY, false);
			response.put(MESSAGE_KEY, "Error opening native directory chooser: " + e.getMessage());
			return ResponseEntity.internalServerError().body(response);
		}
	}

	private void setSystemLookAndFeel() {
		try {
			javax.swing.UIManager.setLookAndFeel(javax.swing.UIManager.getSystemLookAndFeelClassName());
		} catch (Exception e) {
			log.warn("Failed to set system look and feel: {}", e.getMessage());
		}
	}

	@PostMapping("/shutdown")
	public ResponseEntity<Map<String, Object>> shutdown() {
		log.info("Shutdown requested. Initiating graceful shutdown via ShutdownService...");
		shutdownService.initiateShutdown(1000); // 1 second delay
		Map<String, Object> response = new HashMap<>();
		response.put(SUCCESS_KEY, true);
		response.put(MESSAGE_KEY, "Application is shutting down gracefully. Backup will be created.");
		return ResponseEntity.ok(response);
	}
}
