package com.autobuy.provider;

import com.autobuy.exception.SettingsException;

/**
 * Interface defining the contract for managing application
 * configuration/settings.
 */
public interface SettingsProvider {

	/**
	 * Gets the current backup directory path.
	 *
	 * @return The backup directory path
	 */
	String getBackupDir();

	/**
	 * Saves the backup directory path configuration.
	 *
	 * @param backupDir
	 *            The directory path to set
	 * @throws SettingsException
	 *             If saving the configuration fails
	 */
	void saveBackupDir(String backupDir);
}
