package com.autobuy.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseBackupServiceTest {

	@Test
	void testPerformBackup_Success(@TempDir Path tempDir) {
		// Arrange: Use a file-persisted database because H2's 'BACKUP TO' does not
		// perform actions on in-memory DBs
		String dbFilePath = tempDir.resolve("testdb").toAbsolutePath().toString();
		DriverManagerDataSource dataSource = new DriverManagerDataSource();
		dataSource.setDriverClassName("org.h2.Driver");
		dataSource.setUrl("jdbc:h2:file:" + dbFilePath + ";DB_CLOSE_DELAY=-1");
		dataSource.setUsername("sa");
		dataSource.setPassword("");

		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

		// Initialize schema so H2 database has contents to backup
		jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS test (id INT)");

		String backupDirPath = tempDir.resolve("backups").toString();
		DatabaseBackupService service = new DatabaseBackupService(jdbcTemplate);
		ReflectionTestUtils.setField(service, "backupDir", backupDirPath);

		// Act
		service.performBackup();

		// Assert
		// Verify that the backup directory was created
		File directory = new File(backupDirPath);
		assertTrue(directory.exists(), "Backup directory should be created");
		assertTrue(directory.isDirectory(), "Backup path should be a directory");

		// Verify that a zip backup file was created inside the directory
		File[] files = directory.listFiles();
		assertTrue(files != null && files.length > 0, "A backup file should be created");
		assertTrue(files[0].getName().startsWith("backup_"), "Backup file name should start with 'backup_'");
		assertTrue(files[0].getName().endsWith(".zip"), "Backup file should be a ZIP archive");
	}

	@Test
	void testPerformBackup_JdbcError(@TempDir Path tempDir) {
		// Arrange: Point to an invalid JDBC URL to force connection errors
		DriverManagerDataSource dataSource = new DriverManagerDataSource();
		dataSource.setUrl("jdbc:invalid:url");

		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

		String backupDirPath = tempDir.resolve("backups").toString();
		DatabaseBackupService service = new DatabaseBackupService(jdbcTemplate);
		ReflectionTestUtils.setField(service, "backupDir", backupDirPath);

		// Act & Assert
		// The service should catch the JDBC connection error and return cleanly without
		// throwing exceptions
		assertDoesNotThrow(service::performBackup);
	}
}
