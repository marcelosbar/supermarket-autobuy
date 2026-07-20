package com.autobuy.provider;

import com.autobuy.exception.SettingsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;

/**
 * Implementation of SettingsProvider that loads and saves application settings
 * using a local properties file.
 */
@Component
public class PropertiesSettingsProvider extends BasePropertiesProvider implements SettingsProvider {

	private static final Logger log = LoggerFactory.getLogger(PropertiesSettingsProvider.class);
	private static final String BACKUP_DIR_KEY = "autobuy.backup-dir";
	private static final String SETTINGS_CONTEXT = "settings";

	public PropertiesSettingsProvider(@Value("${autobuy.secrets-path:secrets.properties}") String secretsPath) {
		super(secretsPath);
	}

	@PostConstruct
	public void init() {
		loadProperties(log, SETTINGS_CONTEXT);
	}

	@Override
	public synchronized String getBackupDir() {
		loadProperties(log, SETTINGS_CONTEXT);
		return properties.getProperty(BACKUP_DIR_KEY);
	}

	@Override
	public synchronized void saveBackupDir(String backupDir) {
		loadProperties(log, SETTINGS_CONTEXT);
		if (backupDir == null || backupDir.trim().isEmpty()) {
			properties.remove(BACKUP_DIR_KEY);
		} else {
			properties.setProperty(BACKUP_DIR_KEY, backupDir.trim());
		}
		try {
			saveProperties(log, "backup directory");
		} catch (IOException e) {
			throw new SettingsException("Failed to save backup directory to " + secretsPath, e);
		}
	}
}
