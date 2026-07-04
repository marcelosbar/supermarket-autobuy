package com.autobuy.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import com.autobuy.exception.AutoBuyException;
import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;

/**
 * Handles H2 database backups dynamically during application shutdown.
 */
@Service
@Profile("!test")
public class DatabaseBackupService {

	private static final Logger log = LoggerFactory.getLogger(DatabaseBackupService.class);

	private final JdbcTemplate jdbcTemplate;

	@Value("${autobuy.backup-dir:#{null}}")
	private String backupDir;

	@Value("${autobuy.backup.max-history:10}")
	private int maxHistory = 10;

	public DatabaseBackupService(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	/**
	 * Validates the backup directory configuration.
	 */
	@PostConstruct
	public void validateBackupDir() {
		if (backupDir != null) {
			backupDir = backupDir.replace('\\', '/');
		}
		if (backupDir == null || backupDir.isEmpty()) {
			return;
		}
		// Check for Windows drive letter missing separator (e.g. C:Users instead of
		// C:/Users or C:\Users)
		if (backupDir.matches("^[a-zA-Z]:[^\\\\/].*")) {
			String message = String.format("The backup directory '%s' appears to have backslash escaping issues. "
					+ "Please write the path using forward slashes (e.g., C:/Users/...) or double backslashes (e.g., C:\\\\Users\\\\...).",
					backupDir);
			throw new AutoBuyException(message);
		} else if (backupDir.contains("Users") && !backupDir.contains("/") && !backupDir.contains("\\")) {
			// E.g., UsersmarceOneDrive...
			String message = String.format(
					"The backup directory '%s' appears to have backslash escaping issues (contains 'Users' but no path separators). "
							+ "Please write the path using forward slashes (e.g., C:/Users/...) or double backslashes (e.g., C:\\\\Users\\\\...).",
					backupDir);
			throw new AutoBuyException(message);
		}
	}

	public synchronized String getBackupDir() {
		return backupDir;
	}

	public synchronized void setBackupDir(String backupDir) {
		this.backupDir = (backupDir != null) ? backupDir.replace('\\', '/') : null;
		validateBackupDir();
	}

	/**
	 * Executes database backup directly before the Spring context destroys the
	 * datasource bean.
	 */
	@PreDestroy
	@SuppressWarnings("java:S2077")
	public void performBackup() {
		if (backupDir == null || backupDir.trim().isEmpty()) {
			log.info("Database backup is disabled because no backup directory is configured.");
			return;
		}
		log.info("Initiating database snapshot backup...");

		File directory = new File(backupDir);
		if (!directory.exists()) {
			if (directory.mkdirs()) {
				log.info("Created backup directory at {}", backupDir);
			} else {
				log.error("Failed to create backup directory at {}", backupDir);
				return;
			}
		}

		String timestamp = LocalDateTime.now(java.time.ZoneId.systemDefault())
				.format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
		String backupFilePath = new File(directory, "backup_" + timestamp + ".zip").getAbsolutePath();

		try {
			// H2 SQL command to safely back up the database into a compressed zip file
			jdbcTemplate.execute("BACKUP TO '" + backupFilePath + "'");
			log.info("SUCCESS: Database backup saved to {}", backupFilePath);

			// Clean up old backups based on retention policy
			cleanOldBackups(directory);
		} catch (Exception e) {
			log.error("FAILURE: Database backup failed", e);
		}
	}

	private void cleanOldBackups(File directory) {
		try {
			File[] files = directory.listFiles((dir, name) -> name.startsWith("backup_") && name.endsWith(".zip"));
			if (files != null && files.length > maxHistory) {
				// Sort alphabetically, which orders them from oldest to newest chronologically
				Arrays.sort(files, Comparator.comparing(File::getName));

				int filesToDelete = files.length - maxHistory;
				log.info("Backup count ({}) exceeds max history limit ({}). Cleaning up {} oldest backup(s)...",
						files.length, maxHistory, filesToDelete);

				for (int i = 0; i < filesToDelete; i++) {
					deleteBackupFile(files[i]);
				}
			}
		} catch (Exception e) {
			log.error("Error during backup cleanup", e);
		}
	}

	private void deleteBackupFile(File fileToDelete) {
		try {
			java.nio.file.Files.delete(fileToDelete.toPath());
			log.info("Deleted old backup file: {}", fileToDelete.getName());
		} catch (java.io.IOException e) {
			log.warn("Failed to delete old backup file: {}", fileToDelete.getName(), e);
		}
	}
}
