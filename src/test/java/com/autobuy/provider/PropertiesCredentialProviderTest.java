package com.autobuy.provider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

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
}
