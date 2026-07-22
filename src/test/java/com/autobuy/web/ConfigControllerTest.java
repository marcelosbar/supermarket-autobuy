package com.autobuy.web;

import com.autobuy.provider.FolderPicker;
import com.autobuy.provider.SettingsProvider;
import com.autobuy.service.DatabaseBackupService;
import com.autobuy.web.dto.ActionResponse;
import com.autobuy.web.dto.BackupDirRequest;
import com.autobuy.web.dto.BackupDirResponse;
import com.autobuy.web.dto.BackupStatusResponse;
import com.autobuy.web.dto.FolderPickerResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConfigControllerTest {

	@Mock
	private SettingsProvider settingsProvider;

	@Mock
	private DatabaseBackupService databaseBackupService;

	@Mock
	private FolderPicker folderPicker;

	private ConfigController controller;

	@BeforeEach
	void setUp() {
		controller = new ConfigController(settingsProvider, databaseBackupService, folderPicker);
	}

	@Test
	void getBackupDir_configured_returnsBackupDirResponse() {
		// Arrange
		when(settingsProvider.getBackupDir()).thenReturn("/path/to/backup");

		// Act
		ResponseEntity<BackupDirResponse> response = controller.getBackupDir();

		// Assert
		assertNotNull(response.getBody());
		assertEquals("/path/to/backup", response.getBody().backupDir());
	}

	@Test
	void saveBackupDir_validRequest_savesAndReturnsSuccess() {
		// Arrange
		BackupDirRequest request = new BackupDirRequest("/new/path");

		// Act
		ResponseEntity<ActionResponse> response = controller.saveBackupDir(request);

		// Assert
		verify(settingsProvider).saveBackupDir("/new/path");
		verify(databaseBackupService).setBackupDir("/new/path");
		assertNotNull(response.getBody());
		assertTrue(response.getBody().success());
	}

	@Test
	void getBackupStatus_configuredPath_returnsConfiguredStatusTrue() {
		// Arrange
		when(databaseBackupService.getBackupDir()).thenReturn("/path/to/backup");

		// Act
		ResponseEntity<BackupStatusResponse> response = controller.getBackupStatus();

		// Assert
		assertNotNull(response.getBody());
		assertEquals("/path/to/backup", response.getBody().backupDir());
		assertTrue(response.getBody().isConfigured());
	}

	@Test
	void getBackupStatus_nullPath_returnsConfiguredStatusFalse() {
		// Arrange
		when(databaseBackupService.getBackupDir()).thenReturn(null);

		// Act
		ResponseEntity<BackupStatusResponse> response = controller.getBackupStatus();

		// Assert
		assertNotNull(response.getBody());
		assertEquals("", response.getBody().backupDir());
		assertFalse(response.getBody().isConfigured());
	}

	@Test
	void selectNativeDirectory_validSelection_returnsSelectedPath() {
		// Arrange
		when(folderPicker.selectDirectory()).thenReturn("/selected/path");

		// Act
		ResponseEntity<FolderPickerResponse> response = controller.selectNativeDirectory();

		// Assert
		assertNotNull(response.getBody());
		assertTrue(response.getBody().success());
		assertEquals("/selected/path", response.getBody().path());
	}

	@Test
	void selectNativeDirectory_cancelledSelection_returnsCancelledMessage() {
		// Arrange
		when(folderPicker.selectDirectory()).thenReturn(null);

		// Act
		ResponseEntity<FolderPickerResponse> response = controller.selectNativeDirectory();

		// Assert
		assertNotNull(response.getBody());
		assertFalse(response.getBody().success());
		assertEquals("Selection cancelled", response.getBody().message());
	}
}
