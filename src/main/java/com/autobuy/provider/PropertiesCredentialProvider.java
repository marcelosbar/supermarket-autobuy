package com.autobuy.provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import com.autobuy.exception.CredentialException;
import java.io.IOException;

/**
 * Implementation of CredentialProvider that loads credentials from a local
 * properties file.
 */
@Component
public class PropertiesCredentialProvider extends BasePropertiesProvider implements CredentialProvider {

	private static final Logger log = LoggerFactory.getLogger(PropertiesCredentialProvider.class);
	private static final String SECRETS_CONTEXT = "secrets";

	public PropertiesCredentialProvider(@Value("${autobuy.secrets-path:secrets.properties}") String secretsPath) {
		super(secretsPath);
	}

	@PostConstruct
	public void init() {
		loadProperties(log, SECRETS_CONTEXT);
	}

	@Override
	public synchronized String getUsername(String supermarket) {
		loadProperties(log, SECRETS_CONTEXT);
		String key = supermarket.toLowerCase() + ".username";
		return properties.getProperty(key);
	}

	@Override
	public synchronized String getPassword(String supermarket) {
		loadProperties(log, SECRETS_CONTEXT);
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

		loadProperties(log, SECRETS_CONTEXT);
		properties.setProperty(supermarket.toLowerCase() + ".username", username);
		properties.setProperty(supermarket.toLowerCase() + ".password", password);
		try {
			saveProperties(log, "credentials for " + supermarket);
		} catch (IOException e) {
			log.error("Failed to save credentials for {} to {}", supermarket, secretsPath, e);
			throw new CredentialException("Failed to save credentials for " + supermarket, e);
		}
	}
}
