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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import com.autobuy.exception.AutoBuyException;

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
				assertNotEquals(deletedFilename, name,
						deletedFilename + " should have been deleted by retention policy");
			}
		}
	}

	@Test
	void testValidateBackupDir_ValidPaths() {
		DatabaseBackupService service = new DatabaseBackupService(null);

		// Valid absolute path with forward slashes
		ReflectionTestUtils.setField(service, "backupDir", "C:/Users/marce/OneDrive/Applications/SupermarketBackup");
		assertDoesNotThrow(service::validateBackupDir);

		// Valid absolute path with escaped backslashes
		ReflectionTestUtils.setField(service, "backupDir",
				"C:\\\\Users\\\\marce\\\\OneDrive\\\\Applications\\\\SupermarketBackup");
		assertDoesNotThrow(service::validateBackupDir);

		// Valid relative path
		ReflectionTestUtils.setField(service, "backupDir", "./data/backups");
		assertDoesNotThrow(service::validateBackupDir);

		// Null or empty path should not throw
		ReflectionTestUtils.setField(service, "backupDir", "");
		assertDoesNotThrow(service::validateBackupDir);
		ReflectionTestUtils.setField(service, "backupDir", null);
		assertDoesNotThrow(service::validateBackupDir);
	}

	@Test
	void testValidateBackupDir_WindowsDriveBackslashEscapingError() {
		DatabaseBackupService service = new DatabaseBackupService(null);

		// Drive letter but missing separator (escaped backslash issue)
		ReflectionTestUtils.setField(service, "backupDir", "C:UsersmarceOneDriveApplicationsSupermarketBackup");
		AutoBuyException exception = assertThrows(AutoBuyException.class, service::validateBackupDir);
		assertTrue(exception.getMessage().contains("appears to have backslash escaping issues"));
	}

	@Test
	void testValidateBackupDir_RelativePathBackslashEscapingError() {
		DatabaseBackupService service = new DatabaseBackupService(null);

		// Contains 'Users' but no path separators (escaped backslash issue starting
		// from root without drive)
		ReflectionTestUtils.setField(service, "backupDir", "UsersmarceOneDriveApplicationsSupermarketBackup");
		AutoBuyException exception = assertThrows(AutoBuyException.class, service::validateBackupDir);
		assertTrue(exception.getMessage().contains("contains 'Users' but no path separators"));
	}

	@Test
	void testGetAndSetBackupDir() {
		DatabaseBackupService service = new DatabaseBackupService(null);
		service.setBackupDir("./custom");
		org.junit.jupiter.api.Assertions.assertEquals("./custom", service.getBackupDir());
	}

	@Test
	void testPerformBackup_DirCreationFailure() {
		DatabaseBackupService service = new DatabaseBackupService(null);
		ReflectionTestUtils.setField(service, "backupDir", "invalid?dir|path/backup");
		assertDoesNotThrow(service::performBackup);
	}

	@Test
	void testPerformBackup_DeleteIOException(@TempDir Path tempDir) throws Exception {
		String dbFilePath = tempDir.resolve("testdb_io").toAbsolutePath().toString();
		DriverManagerDataSource dataSource = new DriverManagerDataSource();
		dataSource.setDriverClassName("org.h2.Driver");
		dataSource.setUrl("jdbc:h2:file:" + dbFilePath + ";DB_CLOSE_DELAY=-1");
		dataSource.setUsername("sa");
		dataSource.setPassword("");

		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
		jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS test (id INT)");

		Path backupDirPath = tempDir.resolve("backups");
		java.nio.file.Files.createDirectories(backupDirPath);

		for (int i = 0; i < 10; i++) {
			String filename = String.format("backup_20260626_12%02d00.zip", i);
			java.nio.file.Files.createFile(backupDirPath.resolve(filename));
		}

		java.nio.file.Files.delete(backupDirPath.resolve("backup_20260626_120000.zip"));
		File badBackupDir = backupDirPath.resolve("backup_20260626_120000.zip").toFile();
		assertTrue(badBackupDir.mkdir());
		File dummyContent = new File(badBackupDir, "cannotdelete.txt");
		assertTrue(dummyContent.createNewFile());

		DatabaseBackupService service = new DatabaseBackupService(jdbcTemplate);
		ReflectionTestUtils.setField(service, "backupDir", backupDirPath.toString());
		ReflectionTestUtils.setField(service, "maxHistory", 5);

		assertDoesNotThrow(service::performBackup);
	}
}
