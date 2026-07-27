package com.autobuy.web;

import com.autobuy.service.DatabaseBackupService;
import com.autobuy.web.dto.BackupFileResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DatabaseRestoreIT {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private DatabaseBackupService databaseBackupService;

	@Test
	void getBackups_returnsBackupList() throws Exception {
		// Arrange
		when(databaseBackupService.listBackups()).thenReturn(
				List.of(new BackupFileResponse("backup_20260727_200000.zip", 2048L, "2026-07-27T20:00:00Z")));

		// Act & Assert
		mockMvc.perform(get("/api/config/backups")).andExpect(status().isOk())
				.andExpect(jsonPath("$[0].fileName").value("backup_20260727_200000.zip"))
				.andExpect(jsonPath("$[0].sizeBytes").value(2048));
	}

	@Test
	void restoreBackup_blankFileName_returnsBadRequest() throws Exception {
		// Arrange
		String jsonBody = "{\"fileName\":\"\"}";

		// Act & Assert
		mockMvc.perform(post("/api/config/restore").contentType(MediaType.APPLICATION_JSON).content(jsonBody))
				.andExpect(status().isBadRequest());
	}

	@Test
	void restoreBackup_validRequest_triggersRestore() throws Exception {
		// Arrange
		String jsonBody = "{\"fileName\":\"backup_20260727_200000.zip\"}";

		// Act & Assert
		mockMvc.perform(post("/api/config/restore").contentType(MediaType.APPLICATION_JSON).content(jsonBody))
				.andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));

		verify(databaseBackupService).restoreBackup("backup_20260727_200000.zip");
	}

	@Test
	void restoreBackup_serviceThrowsException_returnsBadRequest() throws Exception {
		// Arrange
		String jsonBody = "{\"fileName\":\"backup_corrupt.zip\"}";
		org.mockito.Mockito.doThrow(new com.autobuy.exception.DatabaseRestoreException("Corrupted backup file"))
				.when(databaseBackupService).restoreBackup("backup_corrupt.zip");

		// Act & Assert
		mockMvc.perform(post("/api/config/restore").contentType(MediaType.APPLICATION_JSON).content(jsonBody))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.type").value("RESTORE_ERROR"))
				.andExpect(jsonPath("$.error").value("Corrupted backup file"));
	}
}
