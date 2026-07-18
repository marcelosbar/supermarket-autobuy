package com.autobuy.provider;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;
import org.slf4j.Logger;

/**
 * Base class for providers that load and store configuration in a local
 * properties file.
 */
abstract class BasePropertiesProvider {

	protected final String secretsPath;
	protected final Properties properties = new Properties();

	protected BasePropertiesProvider(String secretsPath) {
		this.secretsPath = secretsPath;
	}

	protected synchronized void loadProperties(Logger log, String context) {
		properties.clear();
		File file = new File(secretsPath);
		if (file.exists()) {
			try (FileInputStream fis = new FileInputStream(file)) {
				properties.load(fis);
				log.info("Successfully loaded {} from {}", context, secretsPath);
			} catch (IOException e) {
				log.error("Failed to load {} properties from {}", context, secretsPath, e);
			}
		} else {
			log.warn("Secrets file '{}' not found for {}. Will use defaults.", secretsPath, context);
		}
	}

	protected synchronized void saveProperties(Logger log, String context) throws IOException {
		try (FileOutputStream fos = new FileOutputStream(secretsPath)) {
			properties.store(fos, "Saved via Web UI");
			log.info("Successfully saved {} to {}", context, secretsPath);
		} catch (IOException e) {
			throw new IOException("Failed to save " + context + " to " + secretsPath, e);
		}
	}
}
