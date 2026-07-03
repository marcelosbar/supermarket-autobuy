package com.autobuy.provider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import com.autobuy.exception.CredentialException;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PropertiesCredentialProviderTest {

	@Test
	void testLoadCredentials_Success(@TempDir Path tempDir) throws IOException {
		// Arrange
		File tempFile = tempDir.resolve("secrets.properties").toFile();
		try (FileWriter writer = new FileWriter(tempFile)) {
			writer.write("""
					continente.username=test-user@example.com
					continente.password=secure-pass123
					""");
		}

		PropertiesCredentialProvider provider = new PropertiesCredentialProvider();
		ReflectionTestUtils.setField(provider, "secretsPath", tempFile.getAbsolutePath());

		// Act
		provider.init();

		// Assert
		assertEquals("test-user@example.com", provider.getUsername("CONTINENTE"));
		assertEquals("secure-pass123", provider.getPassword("CONTINENTE"));

		// Check case insensitivity / lowercase conversion of keys
		assertEquals("test-user@example.com", provider.getUsername("continente"));
		assertEquals("secure-pass123", provider.getPassword("continente"));
	}

	@Test
	void testLoadCredentials_FileNotFound() {
		// Arrange
		PropertiesCredentialProvider provider = new PropertiesCredentialProvider();
		ReflectionTestUtils.setField(provider, "secretsPath", "non-existent-secrets.properties");

		// Act
		provider.init();

		// Assert
		assertNull(provider.getUsername("CONTINENTE"));
		assertNull(provider.getPassword("CONTINENTE"));
	}

	@Test
	void testSaveCredentials_Success(@TempDir Path tempDir) throws Exception {
		// Arrange
		File tempFile = tempDir.resolve("secrets.properties").toFile();
		PropertiesCredentialProvider provider = new PropertiesCredentialProvider();
		ReflectionTestUtils.setField(provider, "secretsPath", tempFile.getAbsolutePath());
		provider.init();

		// Act
		provider.saveCredentials("continente", "new-user@example.com", "new-pass123");

		// Assert
		assertEquals("new-user@example.com", provider.getUsername("continente"));
		assertEquals("new-pass123", provider.getPassword("continente"));

		// Reload and verify persistence
		PropertiesCredentialProvider reloadProvider = new PropertiesCredentialProvider();
		ReflectionTestUtils.setField(reloadProvider, "secretsPath", tempFile.getAbsolutePath());
		reloadProvider.init();
		assertEquals("new-user@example.com", reloadProvider.getUsername("continente"));
		assertEquals("new-pass123", reloadProvider.getPassword("continente"));
	}

	@Test
	void testSaveCredentials_ValidationFailure() {
		PropertiesCredentialProvider provider = new PropertiesCredentialProvider();
		ReflectionTestUtils.setField(provider, "secretsPath", "secrets.properties");

		// Null cases
		assertThrows(CredentialException.class, () -> provider.saveCredentials(null, "user", "pass"));
		assertThrows(CredentialException.class, () -> provider.saveCredentials("continente", null, "pass"));
		assertThrows(CredentialException.class, () -> provider.saveCredentials("continente", "user", null));

		// Empty/Blank cases
		assertThrows(CredentialException.class, () -> provider.saveCredentials("", "user", "pass"));
		assertThrows(CredentialException.class, () -> provider.saveCredentials("continente", "  ", "pass"));
		assertThrows(CredentialException.class, () -> provider.saveCredentials("continente", "user", ""));
	}

	@Test
	void testBackupDir_GetAndSave(@TempDir Path tempDir) throws Exception {
		// Arrange
		File tempFile = tempDir.resolve("secrets.properties").toFile();
		PropertiesCredentialProvider provider = new PropertiesCredentialProvider();
		ReflectionTestUtils.setField(provider, "secretsPath", tempFile.getAbsolutePath());
		provider.init();

		// Act & Assert (initial default/null)
		assertNull(provider.getBackupDir());

		// Act (save path)
		provider.saveBackupDir("C:/MyBackup");

		// Assert
		assertEquals("C:/MyBackup", provider.getBackupDir());

		// Reload and verify persistence
		PropertiesCredentialProvider reloadProvider = new PropertiesCredentialProvider();
		ReflectionTestUtils.setField(reloadProvider, "secretsPath", tempFile.getAbsolutePath());
		reloadProvider.init();
		assertEquals("C:/MyBackup", reloadProvider.getBackupDir());

		// Act (remove path)
		provider.saveBackupDir(null);
		assertNull(provider.getBackupDir());
	}
}
