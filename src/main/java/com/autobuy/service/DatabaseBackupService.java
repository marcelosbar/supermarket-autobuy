package com.autobuy.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
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

	@Value("${autobuy.backup-dir:./data/backups}")
	private String backupDir;

	@Value("${autobuy.backup.max-history:10}")
	private int maxHistory = 10;

	public DatabaseBackupService(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	/**
	 * Executes database backup directly before the Spring context destroys the
	 * datasource bean.
	 */
	@PreDestroy
	public void performBackup() {
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
					File fileToDelete = files[i];
					if (fileToDelete.delete()) {
						log.info("Deleted old backup file: {}", fileToDelete.getName());
					} else {
						log.warn("Failed to delete old backup file: {}", fileToDelete.getName());
					}
				}
			}
		} catch (Exception e) {
			log.error("Error during backup cleanup", e);
		}
	}
}
