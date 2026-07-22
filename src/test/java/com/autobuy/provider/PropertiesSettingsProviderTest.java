package com.autobuy.provider;

import com.autobuy.exception.SettingsException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PropertiesSettingsProviderTest {

	@Test
	void getBackupDirAndSaveBackupDir_validInput_updatesAndPersistsSetting(@TempDir Path tempDir) {
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
	void saveBackupDir_directoryPath_throwsSettingsException() {
		// Arrange
		PropertiesSettingsProvider provider = new PropertiesSettingsProvider("target/");

		// Act & Assert
		assertThrows(SettingsException.class, () -> provider.saveBackupDir("C:/Backup"));
	}

	@Test
	void init_directoryPath_handlesIOException() {
		// Arrange
		PropertiesSettingsProvider provider = new PropertiesSettingsProvider("target/");

		// Act & Assert
		assertDoesNotThrow(provider::init);
	}
}
