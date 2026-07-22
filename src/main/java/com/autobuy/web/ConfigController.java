package com.autobuy.web;

import com.autobuy.provider.FolderPicker;
import com.autobuy.provider.SettingsProvider;
import com.autobuy.service.DatabaseBackupService;
import com.autobuy.web.dto.ActionResponse;
import com.autobuy.web.dto.BackupDirRequest;
import com.autobuy.web.dto.BackupDirResponse;
import com.autobuy.web.dto.BackupStatusResponse;
import com.autobuy.web.dto.FolderPickerResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller exposing backup and environment configuration endpoints.
 */
@RestController
@RequestMapping("/config")
public class ConfigController {

	private static final Logger log = LoggerFactory.getLogger(ConfigController.class);

	private final SettingsProvider settingsProvider;
	private final DatabaseBackupService databaseBackupService;
	private final FolderPicker folderPicker;

	public ConfigController(SettingsProvider settingsProvider,
			@Autowired(required = false) DatabaseBackupService databaseBackupService, FolderPicker folderPicker) {
		this.settingsProvider = settingsProvider;
		this.databaseBackupService = databaseBackupService;
		this.folderPicker = folderPicker;
	}

	@GetMapping("/backup-dir")
	public ResponseEntity<BackupDirResponse> getBackupDir() {
		String dir = (settingsProvider != null) ? settingsProvider.getBackupDir() : null;
		return ResponseEntity.ok(new BackupDirResponse(dir != null ? dir : ""));
	}

	@PostMapping("/backup-dir")
	public ResponseEntity<ActionResponse> saveBackupDir(@RequestBody BackupDirRequest request) {
		String path = request != null ? request.backupDir() : null;
		String resolvedPath = (path == null || path.trim().isEmpty()) ? null : path.trim();

		if (settingsProvider != null) {
			settingsProvider.saveBackupDir(resolvedPath);
		}

		if (databaseBackupService != null) {
			databaseBackupService.setBackupDir(resolvedPath);
		}

		return ResponseEntity.ok(new ActionResponse(true));
	}

	@GetMapping("/backup-status")
	public ResponseEntity<BackupStatusResponse> getBackupStatus() {
		String currentDir = (databaseBackupService != null) ? databaseBackupService.getBackupDir() : null;
		boolean isConfigured = currentDir != null && !currentDir.trim().isEmpty();
		return ResponseEntity.ok(new BackupStatusResponse(currentDir != null ? currentDir : "", isConfigured));
	}

	@PostMapping("/select-native-dir")
	public ResponseEntity<FolderPickerResponse> selectNativeDirectory() {
		log.info("selectNativeDirectory endpoint called.");
		String path = folderPicker.selectDirectory();
		if (path != null) {
			return ResponseEntity.ok(new FolderPickerResponse(true, path));
		} else {
			return ResponseEntity.ok(new FolderPickerResponse(false, null, "Selection cancelled"));
		}
	}
}
