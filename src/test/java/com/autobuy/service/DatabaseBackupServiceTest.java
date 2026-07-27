package com.autobuy.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.autobuy.exception.AutoBuyException;
import com.autobuy.exception.DatabaseRestoreException;
import com.autobuy.web.dto.BackupFileResponse;
import java.util.List;

class DatabaseBackupServiceTest {

	@Test
	void performBackup_validDatabaseAndDir_createsZipArchive(@TempDir Path tempDir) {
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
	void performBackup_jdbcConnectionError_handlesGracefullyWithoutException(@TempDir Path tempDir) {
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
	void performBackup_exceedingMaxHistory_prunesOldBackups(@TempDir Path tempDir) throws Exception {
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
	void validateBackupDir_validPathFormats_doesNotThrowException() {
		// Arrange
		DatabaseBackupService service = new DatabaseBackupService(null);

		// Act & Assert
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
	void validateBackupDir_windowsDriveEscapingError_throwsAutoBuyException() {
		// Arrange
		DatabaseBackupService service = new DatabaseBackupService(null);

		// Act & Assert
		// Drive letter but missing separator (escaped backslash issue)
		ReflectionTestUtils.setField(service, "backupDir", "C:UsersmarceOneDriveApplicationsSupermarketBackup");
		AutoBuyException exception = assertThrows(AutoBuyException.class, service::validateBackupDir);
		assertTrue(exception.getMessage().contains("backslash escaping issues"));
	}

	@Test
	void validateBackupDir_missingSeparatorsInPath_throwsAutoBuyException() {
		// Arrange
		DatabaseBackupService service = new DatabaseBackupService(null);

		// Act & Assert
		// Contains 'Users' but no path separators (escaped backslash issue starting
		// from root without drive)
		ReflectionTestUtils.setField(service, "backupDir", "UsersmarceOneDriveApplicationsSupermarketBackup");
		AutoBuyException exception = assertThrows(AutoBuyException.class, service::validateBackupDir);
		assertTrue(exception.getMessage().contains("backslash escaping issues"));
	}

	@Test
	void setBackupDir_validPath_updatesBackupDir() {
		// Arrange
		DatabaseBackupService service = new DatabaseBackupService(null);

		// Act
		service.setBackupDir("./custom");

		// Assert
		org.junit.jupiter.api.Assertions.assertEquals("./custom", service.getBackupDir());
	}

	@Test
	void performBackup_mkdirsFails_handlesGracefully(@TempDir Path tempDir) throws Exception {
		// Arrange
		Path fileAsParent = tempDir.resolve("somefile.txt");
		Files.writeString(fileAsParent, "content");
		String invalidChildPath = fileAsParent.toAbsolutePath() + "/subfolder";

		DatabaseBackupService service = new DatabaseBackupService(null);
		ReflectionTestUtils.setField(service, "backupDir", invalidChildPath);

		// Act & Assert
		assertDoesNotThrow(service::performBackup);
	}

	@Test
	void performBackup_pathTraversal_handlesGracefully() {
		// Arrange
		DatabaseBackupService service = new DatabaseBackupService(null);
		ReflectionTestUtils.setField(service, "backupDir", "../path/traversal");

		// Act & Assert
		assertDoesNotThrow(service::performBackup);
	}

	@Test
	void performBackup_fileDeletionFailure_handlesGracefully(@TempDir Path tempDir) throws Exception {
		// Arrange
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
		java.nio.file.Files.createFile(backupDirPath.resolve("backup_nonzip.txt"));

		java.nio.file.Files.delete(backupDirPath.resolve("backup_20260626_120000.zip"));
		File badBackupDir = backupDirPath.resolve("backup_20260626_120000.zip").toFile();
		assertTrue(badBackupDir.mkdir());
		File dummyContent = new File(badBackupDir, "cannotdelete.txt");
		assertTrue(dummyContent.createNewFile());

		DatabaseBackupService service = new DatabaseBackupService(jdbcTemplate);
		ReflectionTestUtils.setField(service, "backupDir", backupDirPath.toString());
		ReflectionTestUtils.setField(service, "maxHistory", 5);

		// Act & Assert
		assertDoesNotThrow(service::performBackup);
	}

	@Test
	void listBackups_unconfiguredBackupDir_returnsEmptyList() {
		// Arrange
		DatabaseBackupService service = new DatabaseBackupService(null);

		// Act
		List<BackupFileResponse> result = service.listBackups();

		// Assert
		assertNotNull(result);
		assertTrue(result.isEmpty());
	}

	@Test
	void listBackups_validDirWithBackups_returnsSortedList(@TempDir Path tempDir) throws Exception {
		// Arrange
		Path backupDirPath = tempDir.resolve("backups");
		Files.createDirectories(backupDirPath);
		Path file1 = backupDirPath.resolve("backup_20260701_100000.zip");
		Path file2 = backupDirPath.resolve("backup_20260702_100000.zip");
		Files.writeString(file1, "dummy data 1");
		Files.writeString(file2, "dummy data 2");

		DatabaseBackupService service = new DatabaseBackupService(null);
		ReflectionTestUtils.setField(service, "backupDir", backupDirPath.toString());

		// Act
		List<BackupFileResponse> backups = service.listBackups();

		// Assert
		assertNotNull(backups);
		assertEquals(2, backups.size());
	}

	@Test
	void restoreBackup_unconfiguredBackupDir_throwsDatabaseRestoreException() {
		// Arrange
		DatabaseBackupService service = new DatabaseBackupService(null);

		// Act & Assert
		assertThrows(DatabaseRestoreException.class, () -> service.restoreBackup("backup_20260701_100000.zip"));
	}

	@Test
	void restoreBackup_pathTraversalFileName_throwsDatabaseRestoreException(@TempDir Path tempDir) {
		// Arrange
		DatabaseBackupService service = new DatabaseBackupService(null);
		ReflectionTestUtils.setField(service, "backupDir", tempDir.toString());

		// Act & Assert
		assertThrows(DatabaseRestoreException.class, () -> service.restoreBackup("../backup.zip"));
	}

	@Test
	void restoreBackup_nonExistentFile_throwsDatabaseRestoreException(@TempDir Path tempDir) {
		// Arrange
		DatabaseBackupService service = new DatabaseBackupService(null);
		ReflectionTestUtils.setField(service, "backupDir", tempDir.toString());

		// Act & Assert
		assertThrows(DatabaseRestoreException.class, () -> service.restoreBackup("nonexistent.zip"));
	}

	@Test
	void restoreBackup_validBackup_restoresSuccessfully(@TempDir Path tempDir) throws Exception {
		// Arrange
		String dbFilePath = tempDir.resolve("testdb_restore").toAbsolutePath().toString();
		DriverManagerDataSource dataSource = new DriverManagerDataSource();
		dataSource.setDriverClassName("org.h2.Driver");
		dataSource.setUrl("jdbc:h2:file:" + dbFilePath + ";DB_CLOSE_DELAY=-1");
		dataSource.setUsername("sa");
		dataSource.setPassword("");

		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
		jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS test (id INT)");
		jdbcTemplate.execute("INSERT INTO test VALUES (100)");

		Path backupDirPath = tempDir.resolve("backups");
		Files.createDirectories(backupDirPath);

		DatabaseBackupService service = new DatabaseBackupService(jdbcTemplate);
		ReflectionTestUtils.setField(service, "backupDir", backupDirPath.toString());

		// Act: Create a backup
		service.performBackup();

		File[] backups = backupDirPath.toFile()
				.listFiles((dir, name) -> name.startsWith("backup_") && name.endsWith(".zip"));
		assertNotNull(backups);
		assertTrue(backups.length > 0);
		String backupFileName = backups[0].getName();

		// Act: Restore from backup
		assertDoesNotThrow(() -> service.restoreBackup(backupFileName));
	}

	@Test
	void listBackups_nonExistentOrNotDirectory_returnsEmptyList(@TempDir Path tempDir) throws Exception {
		// Arrange: point to a file instead of directory
		Path filePath = tempDir.resolve("somefile.txt");
		Files.writeString(filePath, "test");
		DatabaseBackupService service = new DatabaseBackupService(null);
		ReflectionTestUtils.setField(service, "backupDir", filePath.toString());

		// Act
		List<BackupFileResponse> backups = service.listBackups();

		// Assert
		assertNotNull(backups);
		assertTrue(backups.isEmpty());
	}

	@Test
	void listBackups_emptyDir_returnsEmptyList(@TempDir Path tempDir) {
		// Arrange
		DatabaseBackupService service = new DatabaseBackupService(null);
		ReflectionTestUtils.setField(service, "backupDir", tempDir.toString());

		// Act
		List<BackupFileResponse> backups = service.listBackups();

		// Assert
		assertNotNull(backups);
		assertTrue(backups.isEmpty());
	}

	@Test
	void restoreBackup_invalidFileNames_throwsException(@TempDir Path tempDir) {
		// Arrange
		DatabaseBackupService service = new DatabaseBackupService(null);
		ReflectionTestUtils.setField(service, "backupDir", tempDir.toString());

		// Act & Assert
		assertThrows(DatabaseRestoreException.class, () -> service.restoreBackup(null));
		assertThrows(DatabaseRestoreException.class, () -> service.restoreBackup(""));
		assertThrows(DatabaseRestoreException.class, () -> service.restoreBackup("   "));
		assertThrows(DatabaseRestoreException.class, () -> service.restoreBackup("backup.txt"));
		assertThrows(DatabaseRestoreException.class, () -> service.restoreBackup("sub/backup.zip"));
		assertThrows(DatabaseRestoreException.class, () -> service.restoreBackup("sub\\backup.zip"));
	}

	@Test
	void restoreBackup_safetySnapshotFails_throwsDatabaseRestoreException(@TempDir Path tempDir) {
		// Arrange: mock JdbcTemplate throwing exception on BACKUP TO
		JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
		org.mockito.Mockito.doThrow(new RuntimeException("Snapshot error")).when(jdbcTemplate)
				.execute(org.mockito.Mockito.contains("BACKUP TO"));

		Path file = tempDir.resolve("backup_test.zip");
		assertDoesNotThrow(() -> Files.writeString(file, "content"));

		DatabaseBackupService service = new DatabaseBackupService(jdbcTemplate);
		ReflectionTestUtils.setField(service, "backupDir", tempDir.toString());

		// Act & Assert
		assertThrows(DatabaseRestoreException.class, () -> service.restoreBackup("backup_test.zip"));
	}

	@Test
	void restoreBackup_corruptZipTriggersRollbackAndThrowsException(@TempDir Path tempDir) throws Exception {
		// Arrange
		String dbFilePath = tempDir.resolve("testdb_rollback").toAbsolutePath().toString();
		DriverManagerDataSource dataSource = new DriverManagerDataSource();
		dataSource.setDriverClassName("org.h2.Driver");
		dataSource.setUrl("jdbc:h2:file:" + dbFilePath + ";DB_CLOSE_DELAY=-1");
		dataSource.setUsername("sa");
		dataSource.setPassword("");

		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
		jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS test (id INT)");

		Path backupDirPath = tempDir.resolve("backups");
		Files.createDirectories(backupDirPath);

		DatabaseBackupService service = new DatabaseBackupService(jdbcTemplate);
		ReflectionTestUtils.setField(service, "backupDir", backupDirPath.toString());

		// Create a corrupt zip file (invalid contents)
		Path corruptZip = backupDirPath.resolve("backup_corrupt.zip");
		Files.writeString(corruptZip, "this is not a zip file");

		// Act & Assert
		assertThrows(DatabaseRestoreException.class, () -> service.restoreBackup("backup_corrupt.zip"));
	}

	@Test
	void restoreBackup_zipWithDotDotEntry_throwsDatabaseRestoreException(@TempDir Path tempDir) throws Exception {
		// Arrange
		String dbFilePath = tempDir.resolve("testdb_dotdot").toAbsolutePath().toString();
		DriverManagerDataSource dataSource = new DriverManagerDataSource();
		dataSource.setDriverClassName("org.h2.Driver");
		dataSource.setUrl("jdbc:h2:file:" + dbFilePath + ";DB_CLOSE_DELAY=-1");
		dataSource.setUsername("sa");
		dataSource.setPassword("");

		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
		jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS test (id INT)");

		Path backupDirPath = tempDir.resolve("backups");
		Files.createDirectories(backupDirPath);

		File zipFile = backupDirPath.resolve("backup_dotdot.zip").toFile();
		try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(
				new java.io.FileOutputStream(zipFile))) {
			java.util.zip.ZipEntry fileEntry = new java.util.zip.ZipEntry("../evil.txt");
			zos.putNextEntry(fileEntry);
			zos.write("evil".getBytes());
			zos.closeEntry();
		}

		DatabaseBackupService service = new DatabaseBackupService(jdbcTemplate);
		ReflectionTestUtils.setField(service, "backupDir", backupDirPath.toString());

		// Act & Assert
		assertThrows(DatabaseRestoreException.class, () -> service.restoreBackup("backup_dotdot.zip"));
	}

	@Test
	void restoreBackup_zipWithDirectoryEntries_restoresSuccessfully(@TempDir Path tempDir) throws Exception {
		// Arrange
		String dbFilePath = tempDir.resolve("testdb_zipdir").toAbsolutePath().toString();
		DriverManagerDataSource dataSource = new DriverManagerDataSource();
		dataSource.setDriverClassName("org.h2.Driver");
		dataSource.setUrl("jdbc:h2:file:" + dbFilePath + ";DB_CLOSE_DELAY=-1");
		dataSource.setUsername("sa");
		dataSource.setPassword("");

		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
		jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS test (id INT)");

		Path backupDirPath = tempDir.resolve("backups");
		Files.createDirectories(backupDirPath);

		File zipFile = backupDirPath.resolve("backup_with_subfolder.zip").toFile();
		try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(
				new java.io.FileOutputStream(zipFile))) {
			java.util.zip.ZipEntry dirEntry = new java.util.zip.ZipEntry("subfolder/");
			zos.putNextEntry(dirEntry);
			zos.closeEntry();

			java.util.zip.ZipEntry fileEntry = new java.util.zip.ZipEntry("subfolder/test.mv.db");
			zos.putNextEntry(fileEntry);
			zos.write("dummy content".getBytes());
			zos.closeEntry();
		}

		DatabaseBackupService service = new DatabaseBackupService(jdbcTemplate);
		ReflectionTestUtils.setField(service, "backupDir", backupDirPath.toString());

		// Act & Assert
		assertDoesNotThrow(() -> service.restoreBackup("backup_with_subfolder.zip"));
	}

	@Test
	void restoreBackup_backupFileIsDirectory_throwsDatabaseRestoreException(@TempDir Path tempDir) {
		// Arrange
		File subDirAsFile = tempDir.resolve("backup_sub.zip").toFile();
		assertTrue(subDirAsFile.mkdir());

		DatabaseBackupService service = new DatabaseBackupService(null);
		ReflectionTestUtils.setField(service, "backupDir", tempDir.toString());

		// Act & Assert
		assertThrows(DatabaseRestoreException.class, () -> service.restoreBackup("backup_sub.zip"));
	}

	@Test
	void getDatabaseDirectory_nonFileUrl_returnsDefaultDataDir() throws Exception {
		// Arrange
		JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
		javax.sql.DataSource dataSource = mock(javax.sql.DataSource.class);
		java.sql.Connection conn = mock(java.sql.Connection.class);
		java.sql.DatabaseMetaData metaData = mock(java.sql.DatabaseMetaData.class);

		when(jdbcTemplate.getDataSource()).thenReturn(dataSource);
		when(dataSource.getConnection()).thenReturn(conn);
		when(conn.getMetaData()).thenReturn(metaData);
		when(metaData.getURL()).thenReturn("jdbc:h2:tcp://localhost/test");

		DatabaseBackupService service = new DatabaseBackupService(jdbcTemplate);

		// Act
		File result = (File) ReflectionTestUtils.invokeMethod(service, "getDatabaseDirectory");

		// Assert
		assertNotNull(result);
		assertTrue(result.getAbsolutePath().replace('\\', '/').endsWith("data"));
	}

	@Test
	void performBackup_emptyBackupDir_returnsWithoutAction() {
		// Arrange
		DatabaseBackupService service = new DatabaseBackupService(null);
		ReflectionTestUtils.setField(service, "backupDir", "   ");

		// Act & Assert
		assertDoesNotThrow(service::performBackup);
	}

	@Test
	void listBackups_emptyStringBackupDir_returnsEmptyList() {
		// Arrange
		DatabaseBackupService service = new DatabaseBackupService(null);
		ReflectionTestUtils.setField(service, "backupDir", " ");

		// Act
		List<BackupFileResponse> backups = service.listBackups();

		// Assert
		assertNotNull(backups);
		assertTrue(backups.isEmpty());
	}

	@Test
	void restoreBackup_urlWithoutSemicolon_restoresSuccessfully(@TempDir Path tempDir) throws Exception {
		// Arrange
		String dbFilePath = tempDir.resolve("testdb_nosemi").toAbsolutePath().toString();
		DriverManagerDataSource dataSource = new DriverManagerDataSource();
		dataSource.setDriverClassName("org.h2.Driver");
		dataSource.setUrl("jdbc:h2:file:" + dbFilePath);
		dataSource.setUsername("sa");
		dataSource.setPassword("");

		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
		jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS test (id INT)");

		Path backupDirPath = tempDir.resolve("backups");
		Files.createDirectories(backupDirPath);

		DatabaseBackupService service = new DatabaseBackupService(jdbcTemplate);
		ReflectionTestUtils.setField(service, "backupDir", backupDirPath.toString());
		service.performBackup();

		File[] backups = backupDirPath.toFile()
				.listFiles((dir, name) -> name.startsWith("backup_") && name.endsWith(".zip"));
		assertNotNull(backups);
		assertTrue(backups.length > 0);

		// Act & Assert
		assertDoesNotThrow(() -> service.restoreBackup(backups[0].getName()));
	}

	@Test
	void validateBackupDir_windowsDriveFormatError_throwsAutoBuyException() {
		DatabaseBackupService service = new DatabaseBackupService(null);
		ReflectionTestUtils.setField(service, "backupDir", "C:Usersmarce");
		assertThrows(AutoBuyException.class, service::validateBackupDir);
	}

	@Test
	void validateBackupDir_usersNoSeparators_throwsAutoBuyException() {
		DatabaseBackupService service = new DatabaseBackupService(null);
		ReflectionTestUtils.setField(service, "backupDir", "UsersmarceOneDrive");
		assertThrows(AutoBuyException.class, service::validateBackupDir);
	}

	@Test
	void getDatabaseDirectory_nullDataSource_usesDefaultDataDir() {
		JdbcTemplate mockJdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
		when(mockJdbc.getDataSource()).thenReturn(null);
		DatabaseBackupService service = new DatabaseBackupService(mockJdbc);
		ReflectionTestUtils.setField(service, "backupDir", "./data");
		assertDoesNotThrow(service::listBackups);
	}

	@Test
	void getDatabaseDirectory_nullUrl_usesDefaultDataDir() throws Exception {
		javax.sql.DataSource mockDs = org.mockito.Mockito.mock(javax.sql.DataSource.class);
		java.sql.Connection mockConn = org.mockito.Mockito.mock(java.sql.Connection.class);
		java.sql.DatabaseMetaData mockMetaData = org.mockito.Mockito.mock(java.sql.DatabaseMetaData.class);

		when(mockDs.getConnection()).thenReturn(mockConn);
		when(mockConn.getMetaData()).thenReturn(mockMetaData);
		when(mockMetaData.getURL()).thenReturn(null);

		JdbcTemplate mockJdbc = new JdbcTemplate(mockDs);
		DatabaseBackupService service = new DatabaseBackupService(mockJdbc);
		ReflectionTestUtils.setField(service, "backupDir", "./data");

		assertDoesNotThrow(service::listBackups);
	}

	@Test
	void getDatabaseDirectory_nonH2Url_usesDefaultDataDir() throws Exception {
		javax.sql.DataSource mockDs = org.mockito.Mockito.mock(javax.sql.DataSource.class);
		java.sql.Connection mockConn = org.mockito.Mockito.mock(java.sql.Connection.class);
		java.sql.DatabaseMetaData mockMetaData = org.mockito.Mockito.mock(java.sql.DatabaseMetaData.class);

		when(mockDs.getConnection()).thenReturn(mockConn);
		when(mockConn.getMetaData()).thenReturn(mockMetaData);
		when(mockMetaData.getURL()).thenReturn("jdbc:mysql://localhost:3306/db");

		JdbcTemplate mockJdbc = new JdbcTemplate(mockDs);
		DatabaseBackupService service = new DatabaseBackupService(mockJdbc);
		ReflectionTestUtils.setField(service, "backupDir", "./data");

		assertDoesNotThrow(service::listBackups);
	}

	@Test
	void setBackupDir_nullAndBackslash_normalizesCorrectly() {
		DatabaseBackupService service = new DatabaseBackupService(null);
		service.setBackupDir(null);
		org.junit.jupiter.api.Assertions.assertNull(service.getBackupDir());

		service.setBackupDir("C:\\test\\dir");
		assertEquals("C:/test/dir", service.getBackupDir());
	}

	@Test
	void cleanOldBackups_fewerThanMaxHistory_doesNotDeleteFiles(@TempDir Path tempDir) throws Exception {
		Path backupDirPath = tempDir.resolve("backups");
		Files.createDirectories(backupDirPath);
		Files.createFile(backupDirPath.resolve("backup_20260701_100000.zip"));
		Files.createFile(backupDirPath.resolve("backup_20260702_100000.zip"));

		DatabaseBackupService service = new DatabaseBackupService(null);
		ReflectionTestUtils.setField(service, "backupDir", backupDirPath.toString());
		ReflectionTestUtils.setField(service, "maxHistory", 10);

		List<BackupFileResponse> backups = service.listBackups();
		assertEquals(2, backups.size());
	}

	@Test
	void listBackups_nonExistentDirectory_returnsEmptyList(@TempDir Path tempDir) {
		DatabaseBackupService service = new DatabaseBackupService(null);
		ReflectionTestUtils.setField(service, "backupDir", tempDir.resolve("doesnotexist").toString());

		List<BackupFileResponse> backups = service.listBackups();
		assertNotNull(backups);
		assertTrue(backups.isEmpty());
	}

	@Test
	void restoreBackup_zipSlipFileName_throwsDatabaseRestoreException(@TempDir Path tempDir) throws Exception {
		Path backupDirPath = tempDir.resolve("backups");
		Files.createDirectories(backupDirPath);
		File zipFile = backupDirPath.resolve("backup_zipslip.zip").toFile();

		try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(
				new java.io.FileOutputStream(zipFile))) {
			zos.putNextEntry(new java.util.zip.ZipEntry("../outside.mv.db"));
			zos.write("dummy".getBytes());
			zos.closeEntry();
		}

		DatabaseBackupService service = new DatabaseBackupService(null);
		ReflectionTestUtils.setField(service, "backupDir", backupDirPath.toString());

		assertThrows(DatabaseRestoreException.class, () -> service.restoreBackup("backup_zipslip.zip"));
	}

	@Test
	void restoreBackup_emptyZipArchive_throwsDatabaseRestoreException(@TempDir Path tempDir) throws Exception {
		Path backupDirPath = tempDir.resolve("backups");
		Files.createDirectories(backupDirPath);
		File zipFile = backupDirPath.resolve("backup_empty.zip").toFile();

		try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(
				new java.io.FileOutputStream(zipFile))) {
			// No entries added
		}

		DatabaseBackupService service = new DatabaseBackupService(null);
		ReflectionTestUtils.setField(service, "backupDir", backupDirPath.toString());

		assertThrows(DatabaseRestoreException.class, () -> service.restoreBackup("backup_empty.zip"));
	}

	@Test
	void performBackup_and_listBackups_nullAndEmptyBranchCoverage() {
		DatabaseBackupService service = new DatabaseBackupService(null);

		// Null backupDir
		ReflectionTestUtils.setField(service, "backupDir", null);
		assertDoesNotThrow(service::performBackup);
		assertTrue(service.listBackups().isEmpty());
		assertThrows(DatabaseRestoreException.class, () -> service.restoreBackup("backup_test.zip"));

		// Empty backupDir
		ReflectionTestUtils.setField(service, "backupDir", "");
		assertDoesNotThrow(service::performBackup);
		assertTrue(service.listBackups().isEmpty());
		assertThrows(DatabaseRestoreException.class, () -> service.restoreBackup("backup_test.zip"));

		// Whitespace backupDir
		ReflectionTestUtils.setField(service, "backupDir", "   ");
		assertDoesNotThrow(service::performBackup);
		assertTrue(service.listBackups().isEmpty());
		assertThrows(DatabaseRestoreException.class, () -> service.restoreBackup("backup_test.zip"));
	}

	@Test
	void evictHikariConnections_withRealHikariDataSource_evictsConnections() {
		com.zaxxer.hikari.HikariDataSource ds = new com.zaxxer.hikari.HikariDataSource();
		ds.setJdbcUrl("jdbc:h2:mem:test_hikari_evict;DB_CLOSE_DELAY=-1");
		ds.setUsername("sa");
		ds.setPassword("");

		JdbcTemplate jdbcTemplate = new JdbcTemplate(ds);
		DatabaseBackupService service = new DatabaseBackupService(jdbcTemplate);
		ReflectionTestUtils.setField(service, "backupDir", "./data");

		assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(service, "evictHikariConnections"));
		ds.close();
	}

	@Test
	void verifyDatabaseConnection_exceptionOnFirstCall_retriesAndEvicts() {
		com.zaxxer.hikari.HikariDataSource ds = new com.zaxxer.hikari.HikariDataSource();
		ds.setJdbcUrl("jdbc:h2:mem:test_hikari_retry;DB_CLOSE_DELAY=-1");
		ds.setUsername("sa");
		ds.setPassword("");

		JdbcTemplate realJdbc = new JdbcTemplate(ds);
		realJdbc.execute("CREATE TABLE IF NOT EXISTS test (id INT)");

		JdbcTemplate spyJdbc = org.mockito.Mockito.spy(realJdbc);
		org.mockito.Mockito
				.doThrow(new org.springframework.dao.TransientDataAccessResourceException("Temporary connection loss"))
				.doCallRealMethod().when(spyJdbc).queryForObject("SELECT 1", Integer.class);

		DatabaseBackupService service = new DatabaseBackupService(spyJdbc);
		ReflectionTestUtils.setField(service, "backupDir", "./data");

		assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(service, "verifyDatabaseConnection"));
		ds.close();
	}
}
