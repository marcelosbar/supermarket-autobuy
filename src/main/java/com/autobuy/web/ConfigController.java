package com.autobuy.web;

import com.autobuy.provider.SettingsProvider;
import com.autobuy.provider.FolderPicker;
import com.autobuy.service.DatabaseBackupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller exposing backup and environment configuration endpoints.
 */
@RestController
@RequestMapping("/api")
public class ConfigController {

	private static final Logger log = LoggerFactory.getLogger(ConfigController.class);

	private final SettingsProvider settingsProvider;
	private final DatabaseBackupService databaseBackupService;
	private final FolderPicker folderPicker;

	private static final String SUCCESS_KEY = "success";
	private static final String MESSAGE_KEY = "message";
	private static final String BACKUP_DIR_KEY = "backupDir";

	public ConfigController(SettingsProvider settingsProvider,
			@Autowired(required = false) DatabaseBackupService databaseBackupService, FolderPicker folderPicker) {
		this.settingsProvider = settingsProvider;
		this.databaseBackupService = databaseBackupService;
		this.folderPicker = folderPicker;
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
		log.info("selectNativeDirectory endpoint called.");
		try {
			String path = folderPicker.selectDirectory();
			Map<String, Object> response = new HashMap<>();
			if (path != null) {
				response.put(SUCCESS_KEY, true);
				response.put("path", path);
			} else {
				response.put(SUCCESS_KEY, false);
				response.put(MESSAGE_KEY, "Selection cancelled");
			}
			return ResponseEntity.ok(response);
		} catch (UnsupportedOperationException e) {
			log.warn(e.getMessage());
			Map<String, Object> response = new HashMap<>();
			response.put(SUCCESS_KEY, false);
			response.put(MESSAGE_KEY, e.getMessage());
			return ResponseEntity.badRequest().body(response);
		} catch (Exception e) {
			log.error("Failed to open native directory chooser", e);
			Map<String, Object> response = new HashMap<>();
			response.put(SUCCESS_KEY, false);
			response.put(MESSAGE_KEY, "Error opening native directory chooser: " + e.getMessage());
			return ResponseEntity.internalServerError().body(response);
		}
	}
}
