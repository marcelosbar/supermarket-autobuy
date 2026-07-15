package com.autobuy.web;

import com.autobuy.provider.SettingsProvider;
import com.autobuy.service.DatabaseBackupService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConfigControllerTest {

	@Test
	void testGetBackupDir_WithNullSettingsProvider() {
		ConfigController controller = new ConfigController(null, null, null);
		ResponseEntity<Map<String, Object>> response = controller.getBackupDir();
		assertEquals("", response.getBody().get("backupDir"));
	}

	@Test
	void testGetBackupDir_WithNonNullSettingsProviderNullDir() {
		SettingsProvider settingsProvider = mock(SettingsProvider.class);
		when(settingsProvider.getBackupDir()).thenReturn(null);

		ConfigController controller = new ConfigController(settingsProvider, null, null);
		ResponseEntity<Map<String, Object>> response = controller.getBackupDir();
		assertEquals("", response.getBody().get("backupDir"));
	}

	@Test
	void testGetBackupDir_WithNonNullSettingsProviderNonNullDir() {
		SettingsProvider settingsProvider = mock(SettingsProvider.class);
		when(settingsProvider.getBackupDir()).thenReturn("C:/backup");

		ConfigController controller = new ConfigController(settingsProvider, null, null);
		ResponseEntity<Map<String, Object>> response = controller.getBackupDir();
		assertEquals("C:/backup", response.getBody().get("backupDir"));
	}

	@Test
	void testSaveBackupDir_WithNullDependenciesAndNullPath() {
		ConfigController controller = new ConfigController(null, null, null);
		Map<String, String> request = new HashMap<>();
		request.put("backupDir", null);

		ResponseEntity<Map<String, Object>> response = controller.saveBackupDir(request);
		assertTrue((Boolean) response.getBody().get("success"));
	}

	@Test
	void testSaveBackupDir_WithNullDependenciesAndBlankPath() {
		ConfigController controller = new ConfigController(null, null, null);
		Map<String, String> request = new HashMap<>();
		request.put("backupDir", "   ");

		ResponseEntity<Map<String, Object>> response = controller.saveBackupDir(request);
		assertTrue((Boolean) response.getBody().get("success"));
	}

	@Test
	void testSaveBackupDir_WithDependenciesAndValidPath() throws IOException {
		SettingsProvider settingsProvider = mock(SettingsProvider.class);
		DatabaseBackupService databaseBackupService = mock(DatabaseBackupService.class);

		ConfigController controller = new ConfigController(settingsProvider, databaseBackupService, null);
		Map<String, String> request = new HashMap<>();
		request.put("backupDir", "C:/backup");

		ResponseEntity<Map<String, Object>> response = controller.saveBackupDir(request);
		assertTrue((Boolean) response.getBody().get("success"));
		verify(settingsProvider).saveBackupDir("C:/backup");
		verify(databaseBackupService).setBackupDir("C:/backup");
	}

	@Test
	void testGetBackupStatus_WithNullService() {
		ConfigController controller = new ConfigController(null, null, null);
		ResponseEntity<Map<String, Object>> response = controller.getBackupStatus();
		assertEquals("", response.getBody().get("backupDir"));
		assertFalse((Boolean) response.getBody().get("isConfigured"));
	}

	@Test
	void testGetBackupStatus_WithNonNullServiceNullDir() {
		DatabaseBackupService service = mock(DatabaseBackupService.class);
		when(service.getBackupDir()).thenReturn(null);

		ConfigController controller = new ConfigController(null, service, null);
		ResponseEntity<Map<String, Object>> response = controller.getBackupStatus();
		assertEquals("", response.getBody().get("backupDir"));
		assertFalse((Boolean) response.getBody().get("isConfigured"));
	}

	@Test
	void testGetBackupStatus_WithNonNullServiceBlankDir() {
		DatabaseBackupService service = mock(DatabaseBackupService.class);
		when(service.getBackupDir()).thenReturn("   ");

		ConfigController controller = new ConfigController(null, service, null);
		ResponseEntity<Map<String, Object>> response = controller.getBackupStatus();
		assertEquals("   ", response.getBody().get("backupDir"));
		assertFalse((Boolean) response.getBody().get("isConfigured"));
	}

	@Test
	void testGetBackupStatus_WithNonNullServiceValidDir() {
		DatabaseBackupService service = mock(DatabaseBackupService.class);
		when(service.getBackupDir()).thenReturn("C:/backup");

		ConfigController controller = new ConfigController(null, service, null);
		ResponseEntity<Map<String, Object>> response = controller.getBackupStatus();
		assertEquals("C:/backup", response.getBody().get("backupDir"));
		assertTrue((Boolean) response.getBody().get("isConfigured"));
	}
}
