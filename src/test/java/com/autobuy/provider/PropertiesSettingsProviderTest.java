package com.autobuy.provider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PropertiesSettingsProviderTest {

	@Test
	void testBackupDir_GetAndSave(@TempDir Path tempDir) throws Exception {
		// Arrange
		File tempFile = tempDir.resolve("secrets.properties").toFile();
		PropertiesSettingsProvider provider = new PropertiesSettingsProvider(tempFile.getAbsolutePath());
		provider.init();

		// Act & Assert (initial default/null)
		assertNull(provider.getBackupDir());

		// Act (save path)
		provider.saveBackupDir("C:/MyBackup");

		// Assert
		assertEquals("C:/MyBackup", provider.getBackupDir());

		// Reload and verify persistence
		PropertiesSettingsProvider reloadProvider = new PropertiesSettingsProvider(tempFile.getAbsolutePath());
		reloadProvider.init();
		assertEquals("C:/MyBackup", reloadProvider.getBackupDir());

		// Act (remove path)
		provider.saveBackupDir(null);
		assertNull(provider.getBackupDir());

		// Act (empty string / blank path)
		provider.saveBackupDir("");
		assertNull(provider.getBackupDir());

		provider.saveBackupDir("   ");
		assertNull(provider.getBackupDir());
	}

	@Test
	void testSaveBackupDir_IOException() {
		PropertiesSettingsProvider provider = new PropertiesSettingsProvider("target/"); // Writing to a directory
																							// throws IOException
		assertThrows(IOException.class, () -> provider.saveBackupDir("C:/Backup"));
	}

	@Test
	void testInit_IOException() {
		PropertiesSettingsProvider provider = new PropertiesSettingsProvider("target/"); // Reading from a directory
																							// throws IOException
		assertDoesNotThrow(provider::init); // catches internally
	}
}
