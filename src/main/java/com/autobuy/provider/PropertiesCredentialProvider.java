package com.autobuy.provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import com.autobuy.exception.CredentialException;

/**
 * Implementation of CredentialProvider that loads credentials from a local
 * properties file.
 */
@Component
public class PropertiesCredentialProvider implements CredentialProvider {

	private static final Logger log = LoggerFactory.getLogger(PropertiesCredentialProvider.class);

	@Value("${autobuy.secrets-path:secrets.properties}")
	private String secretsPath;

	private final Properties properties = new Properties();

	@PostConstruct
	public void init() {
		File file = new File(secretsPath);
		if (file.exists()) {
			try (FileInputStream fis = new FileInputStream(file)) {
				properties.load(fis);
				log.info("Successfully loaded secrets from {}", secretsPath);
			} catch (IOException e) {
				log.error("Failed to load secrets properties from {}", secretsPath, e);
			}
		} else {
			log.warn("Secrets file '{}' not found. Will fall back to interactive prompts.", secretsPath);
		}
	}

	@Override
	public String getUsername(String supermarket) {
		String key = supermarket.toLowerCase() + ".username";
		return properties.getProperty(key);
	}

	@Override
	public String getPassword(String supermarket) {
		String key = supermarket.toLowerCase() + ".password";
		return properties.getProperty(key);
	}

	/**
	 * Saves credentials for a supermarket back to the secrets.properties file.
	 */
	@Override
	public synchronized void saveCredentials(String supermarket, String username, String password)
			throws CredentialException {
		if (supermarket == null || supermarket.trim().isEmpty()) {
			throw new CredentialException("Supermarket name cannot be null or empty");
		}
		if (username == null || username.trim().isEmpty()) {
			throw new CredentialException("Username cannot be null or empty");
		}
		if (password == null || password.trim().isEmpty()) {
			throw new CredentialException("Password cannot be null or empty");
		}

		properties.setProperty(supermarket.toLowerCase() + ".username", username);
		properties.setProperty(supermarket.toLowerCase() + ".password", password);
		try (java.io.FileOutputStream fos = new java.io.FileOutputStream(secretsPath)) {
			properties.store(fos, "Saved via Web UI");
			log.info("Successfully saved credentials for {} to {}", supermarket, secretsPath);
		} catch (IOException e) {
			log.error("Failed to save credentials for {} to {}", supermarket, secretsPath, e);
			throw new CredentialException("Failed to save credentials for " + supermarket, e);
		}
	}
}
