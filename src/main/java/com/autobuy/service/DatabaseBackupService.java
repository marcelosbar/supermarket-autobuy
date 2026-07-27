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
import com.autobuy.exception.DatabaseRestoreException;
import com.autobuy.web.dto.BackupFileResponse;
import java.io.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.*;

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

	@PostConstruct
	public void validateBackupDir() {
		if (backupDir != null)
			backupDir = backupDir.replace('\\', '/');
		if (backupDir == null || backupDir.isEmpty())
			return;
		boolean missingSepDrive = backupDir.matches("^[a-zA-Z]:[^\\\\/].*");
		boolean missingSepUsers = backupDir.contains("Users") && !backupDir.contains("/");
		if (missingSepDrive || missingSepUsers) {
			throw new AutoBuyException(String.format(
					"The backup directory '%s' has backslash escaping issues. Use forward slashes.", backupDir));
		}
	}

	public synchronized String getBackupDir() {
		return backupDir;
	}

	public synchronized void setBackupDir(String backupDir) {
		this.backupDir = (backupDir != null) ? backupDir.replace('\\', '/') : null;
		validateBackupDir();
	}

	@PreDestroy
	@SuppressWarnings("java:S2077")
	public void performBackup() {
		if (backupDir == null || backupDir.trim().isEmpty())
			return;
		File directory = new File(backupDir).getAbsoluteFile();
		if (!directory.exists() && !directory.mkdirs())
			return;
		String ts = LocalDateTime.now(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
		File backupFile = new File(directory, "backup_" + ts + ".zip");
		String safePath = backupFile.getAbsolutePath().replace('\\', '/').replace("'", "''");
		try {
			jdbcTemplate.execute("BACKUP TO '" + safePath + "'");
			log.info("SUCCESS: Database backup saved to {}", safePath);
			cleanOldBackups(directory);
		} catch (Exception e) {
			log.error("FAILURE: Database backup failed", e);
		}
	}

	private void cleanOldBackups(File directory) {
		try {
			File[] files = directory.listFiles((dir, name) -> name.startsWith("backup_") && name.endsWith(".zip"));
			if (files != null && files.length > maxHistory) {
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

	private void deleteBackupFile(File file) {
		try {
			java.nio.file.Files.delete(file.toPath());
			log.info("Deleted old backup file: {}", file.getName());
		} catch (java.io.IOException e) {
			log.warn("Failed to delete old backup file: {}", file.getName(), e);
		}
	}

	public synchronized List<BackupFileResponse> listBackups() {
		if (backupDir == null || backupDir.trim().isEmpty())
			return List.of();
		File directory = new File(backupDir);
		if (!directory.exists() || !directory.isDirectory())
			return List.of();
		File[] files = directory.listFiles((dir, name) -> name.endsWith(".zip"));
		if (files == null || files.length == 0)
			return List.of();
		DateTimeFormatter formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneId.systemDefault());
		return Arrays.stream(files).sorted(Comparator.comparing(File::lastModified).reversed())
				.map(file -> new BackupFileResponse(file.getName(), file.length(),
						formatter.format(Instant.ofEpochMilli(file.lastModified()))))
				.toList();
	}

	/**
	 * Restores the H2 database from a specified backup zip file.
	 *
	 * @param fileName
	 *            Name of the backup file within the backup directory
	 */
	public synchronized void restoreBackup(String fileName) {
		if (backupDir == null || backupDir.trim().isEmpty()) {
			throw new DatabaseRestoreException("Database backup directory is not configured.");
		}
		if (isInvalidFileName(fileName)) {
			throw new DatabaseRestoreException("Invalid backup file name: " + fileName);
		}

		File directory = new File(backupDir);
		File backupFile = new File(directory, fileName);
		if (!backupFile.exists() || !backupFile.isFile()) {
			throw new DatabaseRestoreException("Backup file not found: " + fileName);
		}

		log.info("Initiating database restore from backup: {}", fileName);

		File safetyFile = createSafetySnapshot(directory);
		File dbDir = getDatabaseDirectory();
		shutdownDatabaseAndEvict();

		try {
			extractZipArchive(backupFile, dbDir);
			log.info("Extracted backup archive {} to {}", fileName, dbDir.getAbsolutePath());
			verifyDatabaseConnection();
			log.info("SUCCESS: Database restored successfully from {}", fileName);
		} catch (Exception restoreEx) {
			handleRestoreRollback(fileName, safetyFile, dbDir, restoreEx);
		}
	}

	private File createSafetySnapshot(File directory) {
		String ts = LocalDateTime.now(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
		File file = new File(directory, "backup_before_restore_" + ts + ".zip");
		String path = file.getAbsolutePath().replace('\\', '/').replace("'", "''");
		try {
			jdbcTemplate.execute("BACKUP TO '" + path + "'");
			log.info("Safety snapshot created at {}", path);
			return file;
		} catch (Exception e) {
			log.error("Failed to create safety snapshot before restore", e);
			throw new DatabaseRestoreException("Failed to create safety snapshot before restore: " + e.getMessage(), e);
		}
	}

	private void shutdownDatabaseAndEvict() {
		try {
			jdbcTemplate.execute("SHUTDOWN COMPACT");
		} catch (Exception e) {
			log.warn("H2 SHUTDOWN command produced exception: {}", e.getMessage());
		}
		evictHikariConnections();
	}

	private void handleRestoreRollback(String fileName, File safetyFile, File dbDir, Exception restoreEx) {
		log.error("Restore failed for {}, attempting rollback using safety snapshot {}", fileName, safetyFile.getName(),
				restoreEx);
		try {
			extractZipArchive(safetyFile, dbDir);
			verifyDatabaseConnection();
			log.info("Rollback to safety snapshot successful");
		} catch (Exception rollbackEx) {
			log.error("CRITICAL: Failed to rollback to safety snapshot", rollbackEx);
		}
		throw new DatabaseRestoreException("Database restore failed: " + restoreEx.getMessage(), restoreEx);
	}

	private void evictHikariConnections() {
		try {
			if (jdbcTemplate != null && jdbcTemplate.getDataSource() instanceof com.zaxxer.hikari.HikariDataSource ds
					&& ds.getHikariPoolMXBean() != null) {
				ds.getHikariPoolMXBean().softEvictConnections();
			}
		} catch (Exception e) {
			log.warn("Could not soft-evict Hikari connections: {}", e.getMessage());
		}
	}

	private void verifyDatabaseConnection() {
		evictHikariConnections();
		try {
			jdbcTemplate.queryForObject("SELECT 1", Integer.class);
		} catch (Exception e) {
			log.warn("Initial DB check failed, retrying after evicting connections: {}", e.getMessage());
			evictHikariConnections();
			jdbcTemplate.queryForObject("SELECT 1", Integer.class);
		}
	}

	private boolean isInvalidFileName(String n) {
		if (n == null || n.isBlank() || !n.endsWith(".zip"))
			return true;
		return n.contains("/") || n.contains("\\") || n.contains("..");
	}

	private void validateZipEntryName(String name) {
		if (name.contains("..")) {
			throw new DatabaseRestoreException("Zip entry has invalid name: " + name);
		}
	}

	private void extractZipArchive(File zipFile, File targetDir) throws IOException {
		boolean hasEntries = false;
		try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
			ZipEntry entry;
			while ((entry = zis.getNextEntry()) != null) {
				hasEntries = true;
				String entryName = entry.getName();
				validateZipEntryName(entryName);
				File newFile = new File(targetDir, entryName);
				if (!newFile.getCanonicalPath().startsWith(targetDir.getCanonicalPath() + File.separator)
						&& !newFile.getCanonicalPath().equals(targetDir.getCanonicalPath())) {
					throw new DatabaseRestoreException("Zip entry is outside target directory: " + entryName);
				}
				createDirectory(entry.isDirectory() ? newFile : newFile.getParentFile());
				if (!entry.isDirectory()) {
					try (FileOutputStream fos = new FileOutputStream(newFile)) {
						zis.transferTo(fos);
					}
				}
				zis.closeEntry();
			}
		}
		if (!hasEntries)
			throw new DatabaseRestoreException("Backup archive is empty or invalid: " + zipFile.getName());
	}

	private void createDirectory(File dir) throws IOException {
		if (dir != null && !dir.isDirectory() && !dir.mkdirs()) {
			throw new IOException("Failed to create directory " + dir);
		}
	}

	private File getDatabaseDirectory() {
		try {
			if (jdbcTemplate != null && jdbcTemplate.getDataSource() != null) {
				try (java.sql.Connection conn = jdbcTemplate.getDataSource().getConnection()) {
					String url = conn.getMetaData().getURL();
					if (url != null && url.startsWith("jdbc:h2:file:")) {
						String pathPart = url.substring("jdbc:h2:file:".length());
						int idx = pathPart.indexOf(';');
						if (idx > 0)
							pathPart = pathPart.substring(0, idx);
						File parent = new File(pathPart + ".mv.db").getParentFile();
						if (parent != null)
							return parent.getCanonicalFile();
					}
				}
			}
		} catch (Exception e) {
			log.warn("Could not determine database path: {}", e.getMessage());
		}
		try {
			return new File("./data").getCanonicalFile();
		} catch (Exception e) {
			return new File("./data");
		}
	}
}
