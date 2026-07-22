package com.autobuy.web;

import com.autobuy.provider.SettingsProvider;
import com.autobuy.service.DatabaseBackupService;
import com.autobuy.web.dto.ActionResponse;
import com.autobuy.web.dto.BackupDirRequest;
import com.autobuy.web.dto.BackupDirResponse;
import com.autobuy.web.dto.BackupStatusResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConfigControllerTest {

	@Test
	void testGetBackupDir_WithNullSettingsProvider() {
		ConfigController controller = new ConfigController(null, null, null);
		ResponseEntity<BackupDirResponse> response = controller.getBackupDir();
		assertNotNull(response.getBody());
		assertEquals("", response.getBody().backupDir());
	}

	@Test
	void testGetBackupDir_WithNonNullSettingsProviderNullDir() {
		SettingsProvider settingsProvider = mock(SettingsProvider.class);
		when(settingsProvider.getBackupDir()).thenReturn(null);

		ConfigController controller = new ConfigController(settingsProvider, null, null);
		ResponseEntity<BackupDirResponse> response = controller.getBackupDir();
		assertNotNull(response.getBody());
		assertEquals("", response.getBody().backupDir());
	}

	@Test
	void testGetBackupDir_WithNonNullSettingsProviderNonNullDir() {
		SettingsProvider settingsProvider = mock(SettingsProvider.class);
		when(settingsProvider.getBackupDir()).thenReturn("C:/backup");

		ConfigController controller = new ConfigController(settingsProvider, null, null);
		ResponseEntity<BackupDirResponse> response = controller.getBackupDir();
		assertNotNull(response.getBody());
		assertEquals("C:/backup", response.getBody().backupDir());
	}

	@Test
	void testSaveBackupDir_WithNullDependenciesAndNullPath() {
		ConfigController controller = new ConfigController(null, null, null);
		BackupDirRequest request = new BackupDirRequest(null);

		ResponseEntity<ActionResponse> response = controller.saveBackupDir(request);
		assertNotNull(response.getBody());
		assertTrue(response.getBody().success());
	}

	@Test
	void testSaveBackupDir_WithNullDependenciesAndBlankPath() {
		ConfigController controller = new ConfigController(null, null, null);
		BackupDirRequest request = new BackupDirRequest("   ");

		ResponseEntity<ActionResponse> response = controller.saveBackupDir(request);
		assertNotNull(response.getBody());
		assertTrue(response.getBody().success());
	}

	@Test
	void testSaveBackupDir_WithDependenciesAndValidPath() {
		SettingsProvider settingsProvider = mock(SettingsProvider.class);
		DatabaseBackupService databaseBackupService = mock(DatabaseBackupService.class);

		ConfigController controller = new ConfigController(settingsProvider, databaseBackupService, null);
		BackupDirRequest request = new BackupDirRequest("C:/backup");

		ResponseEntity<ActionResponse> response = controller.saveBackupDir(request);
		assertNotNull(response.getBody());
		assertTrue(response.getBody().success());
		verify(settingsProvider).saveBackupDir("C:/backup");
		verify(databaseBackupService).setBackupDir("C:/backup");
	}

	@Test
	void testSaveBackupDir_WithInvalidPath() {
		ConfigController controller = new ConfigController(null, null, null);
		BackupDirRequest request = new BackupDirRequest("../invalid");

		ResponseEntity<ActionResponse> response = controller.saveBackupDir(request);
		assertNotNull(response.getBody());
		assertFalse(response.getBody().success());
		assertEquals(400, response.getStatusCode().value());
	}

	@Test
	void testGetBackupStatus_WithNullService() {
		ConfigController controller = new ConfigController(null, null, null);
		ResponseEntity<BackupStatusResponse> response = controller.getBackupStatus();
		assertNotNull(response.getBody());
		assertEquals("", response.getBody().backupDir());
		assertFalse(response.getBody().isConfigured());
	}

	@Test
	void testGetBackupStatus_WithNonNullServiceNullDir() {
		DatabaseBackupService service = mock(DatabaseBackupService.class);
		when(service.getBackupDir()).thenReturn(null);

		ConfigController controller = new ConfigController(null, service, null);
		ResponseEntity<BackupStatusResponse> response = controller.getBackupStatus();
		assertNotNull(response.getBody());
		assertEquals("", response.getBody().backupDir());
		assertFalse(response.getBody().isConfigured());
	}

	@Test
	void testGetBackupStatus_WithNonNullServiceBlankDir() {
		DatabaseBackupService service = mock(DatabaseBackupService.class);
		when(service.getBackupDir()).thenReturn("   ");

		ConfigController controller = new ConfigController(null, service, null);
		ResponseEntity<BackupStatusResponse> response = controller.getBackupStatus();
		assertNotNull(response.getBody());
		assertEquals("   ", response.getBody().backupDir());
		assertFalse(response.getBody().isConfigured());
	}

	@Test
	void testGetBackupStatus_WithNonNullServiceValidDir() {
		DatabaseBackupService service = mock(DatabaseBackupService.class);
		when(service.getBackupDir()).thenReturn("C:/backup");

		ConfigController controller = new ConfigController(null, service, null);
		ResponseEntity<BackupStatusResponse> response = controller.getBackupStatus();
		assertNotNull(response.getBody());
		assertEquals("C:/backup", response.getBody().backupDir());
		assertTrue(response.getBody().isConfigured());
	}
}
