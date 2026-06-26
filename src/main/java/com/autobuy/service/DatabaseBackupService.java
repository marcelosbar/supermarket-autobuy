package com.autobuy.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Handles H2 database backups dynamically during application shutdown.
 */
@Service
public class DatabaseBackupService {

	private static final Logger log = LoggerFactory.getLogger(DatabaseBackupService.class);

	private final JdbcTemplate jdbcTemplate;

	@Value("${autobuy.backup-dir:./data/backups}")
	private String backupDir;

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

		String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
		String backupFilePath = new File(directory, "backup_" + timestamp + ".zip").getAbsolutePath();

		try {
			// H2 SQL command to safely back up the database into a compressed zip file
			jdbcTemplate.execute("BACKUP TO '" + backupFilePath + "'");
			log.info("SUCCESS: Database backup saved to {}", backupFilePath);
		} catch (Exception e) {
			log.error("FAILURE: Database backup failed", e);
		}
	}
}
