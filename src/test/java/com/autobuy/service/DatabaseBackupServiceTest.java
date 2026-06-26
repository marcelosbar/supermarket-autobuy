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

	@Test
	void testPerformBackup_RetentionPolicyApplied(@TempDir Path tempDir) throws Exception {
		// Arrange: Setup H2 datasource
		String dbFilePath = tempDir.resolve("testdb_ret").toAbsolutePath().toString();
		DriverManagerDataSource dataSource = new DriverManagerDataSource();
		dataSource.setDriverClassName("org.h2.Driver");
		dataSource.setUrl("jdbc:h2:file:" + dbFilePath + ";DB_CLOSE_DELAY=-1");
		dataSource.setUsername("sa");
		dataSource.setPassword("");

		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
		jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS test (id INT)");

		// Create backups directory
		Path backupDirPath = tempDir.resolve("backups");
		java.nio.file.Files.createDirectories(backupDirPath);

		// Create 12 dummy files named backup_20260626_120000.zip to
		// backup_20260626_120011.zip
		for (int i = 0; i < 12; i++) {
			String filename = String.format("backup_20260626_12%02d00.zip", i);
			java.nio.file.Files.createFile(backupDirPath.resolve(filename));
		}

		DatabaseBackupService service = new DatabaseBackupService(jdbcTemplate);
		ReflectionTestUtils.setField(service, "backupDir", backupDirPath.toString());
		ReflectionTestUtils.setField(service, "maxHistory", 5); // set maxHistory to 5

		// Act: perform backup (will create a 13th file with current timestamp, then
		// prune down to 5)
		service.performBackup();

		// Assert
		File directory = new File(backupDirPath.toString());
		File[] files = directory.listFiles((dir, name) -> name.startsWith("backup_") && name.endsWith(".zip"));

		// There should be exactly 5 backups remaining
		assertTrue(files != null && files.length == 5, "Should prune backup folder to maxHistory = 5");

		// The new backup is created with current timestamp (presumably after
		// 20260626_120011.zip)
		// So the remaining files should be the newest files.
		// The oldest dummy files (indices 0 to 7) should have been deleted.
		for (File file : files) {
			String name = file.getName();
			// backup_20260626_120000.zip to backup_20260626_120007.zip should be deleted
			for (int j = 0; j <= 7; j++) {
				String deletedFilename = String.format("backup_20260626_12%02d00.zip", j);
				assertTrue(!name.equals(deletedFilename),
						deletedFilename + " should have been deleted by retention policy");
			}
		}
	}
}
