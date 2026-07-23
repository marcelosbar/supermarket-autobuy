package com.autobuy.web;

import com.autobuy.exception.CredentialException;
import com.autobuy.exception.DriverException;
import com.autobuy.exception.SettingsException;
import com.autobuy.exception.ShoppingListException;
import com.autobuy.provider.CredentialProvider;
import com.autobuy.provider.FolderPicker;
import com.autobuy.provider.SettingsProvider;
import com.autobuy.provider.ShoppingListProvider;
import com.autobuy.service.ShutdownService;
import com.autobuy.service.DatabaseBackupService;
import com.autobuy.service.AutoBuyExecutionContext;
import com.autobuy.service.AutoBuyOrchestrationService;
import com.autobuy.service.GuestSearchService;
import com.autobuy.service.ProductResolutionService;
import com.autobuy.model.ProductMapping;
import com.autobuy.config.MemoryAppender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AutoBuyControllerIT {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private CredentialProvider credentialProvider;

	@Autowired
	private SettingsProvider settingsProvider;

	@MockitoBean
	private ShutdownService shutdownService;

	@MockitoBean
	private DatabaseBackupService databaseBackupService;

	@MockitoBean
	private AutoBuyOrchestrationService autoBuyOrchestrationService;

	@MockitoBean
	private AutoBuyExecutionContext autoBuyExecutionContext;

	@MockitoBean
	private ProductResolutionService productResolutionService;

	@MockitoBean
	private GuestSearchService guestSearchService;

	@MockitoBean
	private com.autobuy.service.ProductService productService;

	@MockitoBean
	private ShoppingListProvider shoppingListProvider;

	@MockitoBean
	private FolderPicker folderPicker;

	@BeforeEach
	void setUp() {
		if (credentialProvider instanceof StubCredentialProvider stub) {
			stub.username = "test-user";
			stub.password = "test-password";
			stub.throwUnsupported = false;
			stub.throwCredentialException = false;
			stub.throwMessage = "";
		}
		if (settingsProvider instanceof StubSettingsProvider stub) {
			stub.backupDir = "C:/mock-backup";
			stub.throwBackupDirException = false;
		}
		when(shoppingListProvider.getShoppingList(anyString())).thenReturn(List.of());

		// Programmatically configure MemoryAppender for the integration tests
		ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory
				.getLogger("com.autobuy");
		if (logger.getAppender("WEB_MEMORY_APPENDER") == null) {
			MemoryAppender appender = new MemoryAppender();
			appender.setName("WEB_MEMORY_APPENDER");
			appender.setContext((ch.qos.logback.classic.LoggerContext) org.slf4j.LoggerFactory.getILoggerFactory());
			appender.start();
			logger.addAppender(appender);
		}
		MemoryAppender.clear();
	}

	@Test
	void getCredentialsStatus_validSupermarket_returnsStatusOk() throws Exception {
		// Arrange & Act & Assert
		mockMvc.perform(get("/api/credentials?supermarket=CONTINENTE")).andExpect(status().isOk());
	}

	@Test
	void getShoppingList_validRequest_returnsShoppingList() throws Exception {
		// Arrange & Act & Assert
		mockMvc.perform(get("/api/shopping-list")).andExpect(status().isOk());
	}

	@Test
	void getMappings_validRequest_returnsMappingsList() throws Exception {
		// Arrange & Act & Assert
		mockMvc.perform(get("/api/mappings")).andExpect(status().isOk());
	}

	@Test
	void saveCredentials_validCredentials_savesSuccessfully() throws Exception {
		// Arrange
		String json = """
				{
					"supermarket": "CONTINENTE",
					"username": "new-user@example.com",
					"password": "new-password"
				}
				""";

		// Act & Assert
		mockMvc.perform(post("/api/credentials").contentType(MediaType.APPLICATION_JSON).content(json))
				.andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.message").value("Credentials saved successfully."));

		if (credentialProvider instanceof StubCredentialProvider stub) {
			org.junit.jupiter.api.Assertions.assertEquals("new-user@example.com", stub.username);
			org.junit.jupiter.api.Assertions.assertEquals("new-password", stub.password);
		}
	}

	@Test
	void saveCredentials_credentialException_returnsBadRequest() throws Exception {
		// Arrange
		if (credentialProvider instanceof StubCredentialProvider stub) {
			stub.throwCredentialException = true;
			stub.throwMessage = "Validation failed: username cannot be empty";
		}

		String json = """
				{
					"supermarket": "CONTINENTE",
					"username": "test-user",
					"password": "new-password"
				}
				""";

		// Act & Assert
		mockMvc.perform(post("/api/credentials").contentType(MediaType.APPLICATION_JSON).content(json))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.type").value("CREDENTIAL_ERROR"))
				.andExpect(jsonPath("$.error").value("Validation failed: username cannot be empty"));
	}

	@Test
	void saveCredentials_unsupportedOperation_returnsBadRequest() throws Exception {
		// Arrange
		if (credentialProvider instanceof StubCredentialProvider stub) {
			stub.throwUnsupported = true;
		}

		String json = """
				{
					"supermarket": "CONTINENTE",
					"username": "new-user@example.com",
					"password": "new-password"
				}
				""";

		// Act & Assert
		mockMvc.perform(post("/api/credentials").contentType(MediaType.APPLICATION_JSON).content(json))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.type").value("UNSUPPORTED_OPERATION"))
				.andExpect(jsonPath("$.error").value("Saving credentials is not supported by this provider."));
	}

	@Test
	void getBackupDir_validRequest_returnsBackupDirResponse() throws Exception {
		// Arrange & Act & Assert
		mockMvc.perform(get("/api/config/backup-dir")).andExpect(status().isOk())
				.andExpect(jsonPath("$.backupDir").exists());
	}

	@Test
	void saveBackupDir_validPath_returnsSuccessResponse() throws Exception {
		// Arrange
		String json = """
				{
					"backupDir": "C:/mock-backup"
				}
				""";

		// Act & Assert
		mockMvc.perform(post("/api/config/backup-dir").contentType(MediaType.APPLICATION_JSON).content(json))
				.andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
	}

	@Test
	void getBackupStatus_configuredPath_returnsBackupStatus() throws Exception {
		// Arrange & Act & Assert
		mockMvc.perform(get("/api/config/backup-status")).andExpect(status().isOk())
				.andExpect(jsonPath("$.backupDir").exists()).andExpect(jsonPath("$.isConfigured").exists());
	}

	@Test
	void getBackupStatus_notConfigured_returnsNotConfiguredStatus() throws Exception {
		// Arrange
		if (settingsProvider instanceof StubSettingsProvider stub) {
			stub.backupDir = null;
		}
		try {
			// Act & Assert
			mockMvc.perform(get("/api/config/backup-status")).andExpect(status().isOk())
					.andExpect(jsonPath("$.backupDir").value("")).andExpect(jsonPath("$.isConfigured").value(false));
		} finally {
			if (settingsProvider instanceof StubSettingsProvider stub) {
				stub.backupDir = "C:/mock-backup";
			}
		}
	}

	@Test
	void getCredentialsStatus_nullCredentials_returnsHasFlagsFalse() throws Exception {
		// Arrange
		if (credentialProvider instanceof StubCredentialProvider stub) {
			stub.username = null;
			stub.password = null;
		}
		try {
			// Act & Assert
			mockMvc.perform(get("/api/credentials?supermarket=CONTINENTE")).andExpect(status().isOk())
					.andExpect(jsonPath("$.hasUsername").value(false)).andExpect(jsonPath("$.hasPassword").value(false))
					.andExpect(jsonPath("$.username").value(""));
		} finally {
			if (credentialProvider instanceof StubCredentialProvider stub) {
				stub.username = "test-user";
				stub.password = "test-password";
			}
		}
	}

	@Test
	void getCredentialsStatus_blankCredentials_returnsHasFlagsFalse() throws Exception {
		// Arrange
		if (credentialProvider instanceof StubCredentialProvider stub) {
			stub.username = "   ";
			stub.password = "   ";
		}
		try {
			// Act & Assert
			mockMvc.perform(get("/api/credentials?supermarket=CONTINENTE")).andExpect(status().isOk())
					.andExpect(jsonPath("$.hasUsername").value(false)).andExpect(jsonPath("$.hasPassword").value(false))
					.andExpect(jsonPath("$.username").value("   "));
		} finally {
			if (credentialProvider instanceof StubCredentialProvider stub) {
				stub.username = "test-user";
				stub.password = "test-password";
			}
		}
	}

	@Test
	void saveCredentials_blankPassword_returnsBadRequest() throws Exception {
		// Arrange
		String json = """
				{
					"supermarket": "CONTINENTE",
					"username": "existing-user",
					"password": ""
				}
				""";
		// Act & Assert
		mockMvc.perform(post("/api/credentials").contentType(MediaType.APPLICATION_JSON).content(json))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.type").value("VALIDATION_ERROR"));
	}

	@Test
	void saveBackupDir_emptyPath_returnsSuccessResponse() throws Exception {
		// Arrange
		String json = """
				{
					"backupDir": ""
				}
				""";

		// Act & Assert
		mockMvc.perform(post("/api/config/backup-dir").contentType(MediaType.APPLICATION_JSON).content(json))
				.andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
	}

	@Test
	void saveBackupDir_providerException_returnsBadRequest() throws Exception {
		// Arrange
		if (settingsProvider instanceof StubSettingsProvider stub) {
			stub.throwBackupDirException = true;
		}
		try {
			String json = """
					{
						"backupDir": "C:/mock-backup"
					}
					""";
			// Act & Assert
			mockMvc.perform(post("/api/config/backup-dir").contentType(MediaType.APPLICATION_JSON).content(json))
					.andExpect(status().isBadRequest()).andExpect(jsonPath("$.type").value("SETTINGS_ERROR"))
					.andExpect(jsonPath("$.error").value("Failed to save backup dir"));
		} finally {
			if (settingsProvider instanceof StubSettingsProvider stub) {
				stub.throwBackupDirException = false;
			}
		}
	}

	@Test
	void selectNativeDirectory_validSelection_returnsSelectedPath() throws Exception {
		// Arrange
		when(folderPicker.selectDirectory()).thenReturn("/custom/backup/dir");

		// Act & Assert
		mockMvc.perform(post("/api/config/select-native-dir")).andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.path").value("/custom/backup/dir"));
	}

	@Test
	void selectNativeDirectory_cancelled_returnsCancelledMessage() throws Exception {
		// Arrange
		when(folderPicker.selectDirectory()).thenReturn(null);

		// Act & Assert
		mockMvc.perform(post("/api/config/select-native-dir")).andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.message").value("Selection cancelled"));
	}

	@Test
	void selectNativeDirectory_runtimeException_returnsInternalServerError() throws Exception {
		// Arrange
		when(folderPicker.selectDirectory()).thenThrow(new RuntimeException("Drive not ready"));

		// Act & Assert
		mockMvc.perform(post("/api/config/select-native-dir")).andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.type").value("INTERNAL_ERROR"))
				.andExpect(jsonPath("$.error").value("Drive not ready"));
	}

	@Test
	void selectNativeDirectory_headlessEnvironment_returnsBadRequest() throws Exception {
		// Arrange
		when(folderPicker.selectDirectory()).thenThrow(new UnsupportedOperationException(
				"Cannot open native folder picker: Graphics environment is headless."));

		// Act & Assert
		mockMvc.perform(post("/api/config/select-native-dir")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.type").value("UNSUPPORTED_OPERATION")).andExpect(jsonPath("$.error")
						.value("Cannot open native folder picker: Graphics environment is headless."));
	}

	@Test
	void searchSupermarket_validQuery_returnsSearchResults() throws Exception {
		// Arrange
		var dummyResult = new com.autobuy.model.SearchResult("sku", "Product", "Brand", java.math.BigDecimal.TEN, "url",
				"cat");
		when(guestSearchService.performGuestSearch("milk", "CONTINENTE")).thenReturn(List.of(dummyResult));

		// Act & Assert
		mockMvc.perform(get("/api/autobuy/search").param("query", "milk").param("supermarket", "CONTINENTE"))
				.andExpect(status().isOk()).andExpect(jsonPath("$[0].externalId").value("sku"))
				.andExpect(jsonPath("$[0].name").value("Product"));
	}

	@Test
	void searchSupermarket_searchFailure_returnsInternalServerError() throws Exception {
		// Arrange
		when(guestSearchService.performGuestSearch("milk", "CONTINENTE"))
				.thenThrow(new RuntimeException("Search failed"));

		// Act & Assert
		mockMvc.perform(get("/api/autobuy/search").param("query", "milk").param("supermarket", "CONTINENTE"))
				.andExpect(status().isInternalServerError()).andExpect(jsonPath("$.type").value("INTERNAL_ERROR"));
	}

	@Test
	void shutdown_validRequest_initiatesGracefulShutdown() throws Exception {
		// Arrange & Act & Assert
		mockMvc.perform(post("/api/system/shutdown")).andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.message")
						.value("Application is shutting down gracefully. Backup will be created."));

		verify(shutdownService).initiateShutdown(1000);
	}

	@TestConfiguration
	static class TestConfig {
		@Bean
		@Primary
		public StubCredentialProvider stubCredentialProvider() {
			return new StubCredentialProvider();
		}

		@Bean
		@Primary
		public StubSettingsProvider stubSettingsProvider() {
			return new StubSettingsProvider();
		}
	}

	private static class StubCredentialProvider implements CredentialProvider {
		private String username = "test-user";
		private String password = "test-password";
		private boolean throwUnsupported = false;
		private boolean throwCredentialException = false;
		private String throwMessage = "";

		@Override
		public String getUsername(String supermarket) {
			return username;
		}

		@Override
		public String getPassword(String supermarket) {
			return password;
		}

		@Override
		public void saveCredentials(String supermarket, String username, String password) throws CredentialException {
			if (throwUnsupported) {
				throw new UnsupportedOperationException("Saving credentials is not supported by this provider.");
			}
			if (throwCredentialException) {
				throw new CredentialException(throwMessage);
			}
			this.username = username;
			this.password = password;
		}
	}

	private static class StubSettingsProvider implements SettingsProvider {
		private String backupDir = "C:/mock-backup";
		private boolean throwBackupDirException = false;

		@Override
		public String getBackupDir() {
			return backupDir;
		}

		@Override
		public void saveBackupDir(String backupDir) {
			if (throwBackupDirException) {
				throw new SettingsException("Failed to save backup dir");
			}
			this.backupDir = backupDir;
		}
	}

	@Test
	void deleteMapping_nonexistentId_returnsNotFound() throws Exception {
		// Arrange & Act & Assert
		mockMvc.perform(delete("/api/mappings/9999")).andExpect(status().isNotFound());
	}

	@Test
	void deleteMapping_existingId_deletesAndReturnsNoContent() throws Exception {
		// Arrange
		ProductMapping mapping = new ProductMapping("query-del", "CONTINENTE", "sku-del", "Product Delete");
		when(productService.findMappingById(1L)).thenReturn(Optional.of(mapping));

		// Act & Assert
		mockMvc.perform(delete("/api/mappings/1")).andExpect(status().isNoContent());

		verify(productService).deleteMapping(1L);
	}

	@Test
	void runAutoBuy_validSupermarket_startsAutoBuy() throws Exception {
		// Arrange
		String json = """
				{
					"supermarket": "CONTINENTE"
				}
				""";

		// Act & Assert
		mockMvc.perform(post("/api/autobuy/run").contentType(MediaType.APPLICATION_JSON).content(json))
				.andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.message").value("Auto-Buy started successfully."));

		verify(autoBuyOrchestrationService).startAutoBuy("shopping-list.json", "CONTINENTE", false);
	}

	@Test
	void runAutoBuy_headlessTrue_startsAutoBuyInHeadlessMode() throws Exception {
		// Arrange
		String json = """
				{
					"supermarket": "CONTINENTE",
					"headless": true
				}
				""";

		// Act & Assert
		mockMvc.perform(post("/api/autobuy/run").contentType(MediaType.APPLICATION_JSON).content(json))
				.andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));

		verify(autoBuyOrchestrationService).startAutoBuy("shopping-list.json", "CONTINENTE", true);
	}

	@Test
	void runAutoBuy_nullRequest_usesDefaultSupermarketAndHeadlessFalse() throws Exception {
		// Arrange & Act & Assert
		mockMvc.perform(post("/api/autobuy/run")).andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true));

		verify(autoBuyOrchestrationService).startAutoBuy("shopping-list.json", "CONTINENTE", false);
	}

	@Test
	void runAutoBuy_blankSupermarket_returnsBadRequest() throws Exception {
		// Arrange
		String json = "{}";

		// Act & Assert
		mockMvc.perform(post("/api/autobuy/run").contentType(MediaType.APPLICATION_JSON).content(json))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.type").value("VALIDATION_ERROR"));
	}

	@Test
	void runAutoBuy_illegalStateException_returnsConflict() throws Exception {
		// Arrange
		doThrow(new IllegalStateException("Already running")).when(autoBuyOrchestrationService)
				.startAutoBuy("shopping-list.json", "CONTINENTE", false);

		String json = """
				{
					"supermarket": "CONTINENTE"
				}
				""";

		// Act & Assert
		mockMvc.perform(post("/api/autobuy/run").contentType(MediaType.APPLICATION_JSON).content(json))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.type").value("STATE_ERROR"))
				.andExpect(jsonPath("$.error").value("Already running"));
	}

	@Test
	void getAutoBuyStatus_activeExecution_returnsAutoBuyStatus() throws Exception {
		// Arrange
		var dummyResult = new com.autobuy.model.SearchResult("sku", "Product", "Brand", java.math.BigDecimal.TEN, "url",
				"cat");

		when(autoBuyExecutionContext.getState()).thenReturn(com.autobuy.model.AutoBuyState.RUNNING);
		when(autoBuyExecutionContext.getCurrentItemQuery()).thenReturn("query");
		when(autoBuyExecutionContext.getCurrentItemQuantity()).thenReturn(5);
		when(autoBuyExecutionContext.getSearchResults()).thenReturn(List.of(dummyResult));
		when(autoBuyExecutionContext.getErrorMsg()).thenReturn("");
		when(autoBuyExecutionContext.getSkippedItems()).thenReturn(List.of());
		when(autoBuyExecutionContext.getExhaustedItems()).thenReturn(List.of());
		when(autoBuyExecutionContext.isBrowserOpen()).thenReturn(false);
		when(autoBuyExecutionContext.getMappingInstructions()).thenReturn("");

		MemoryAppender.clear();
		org.slf4j.LoggerFactory.getLogger("com.autobuy").info("log line");

		// Act & Assert
		mockMvc.perform(get("/api/autobuy/status")).andExpect(status().isOk())
				.andExpect(jsonPath("$.state").value("RUNNING"))
				.andExpect(jsonPath("$.currentItemQuery").value("query"))
				.andExpect(jsonPath("$.currentItemQuantity").value(5))
				.andExpect(jsonPath("$.searchResults[0].externalId").value("sku"))
				.andExpect(jsonPath("$.logs[0]").value("INFO - log line"));
	}

	@Test
	void resolveMappingEndpoint_validResolution_resolvesMappingSuccessfully() throws Exception {
		// Arrange
		String json = """
				{
					"externalId": "sku123",
					"saveMapping": true
				}
				""";

		// Act & Assert
		mockMvc.perform(post("/api/autobuy/resolve").contentType(MediaType.APPLICATION_JSON).content(json))
				.andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));

		verify(productResolutionService).resolveMapping("sku123", true);
	}

	@Test
	void resolveMappingEndpoint_illegalArgumentException_returnsBadRequest() throws Exception {
		// Arrange
		doThrow(new IllegalArgumentException("Invalid ID")).when(productResolutionService)
				.resolveMapping(eq("invalid-sku"), anyBoolean());

		String json = """
				{
					"externalId": "invalid-sku",
					"saveMapping": false
				}
				""";

		// Act & Assert
		mockMvc.perform(post("/api/autobuy/resolve").contentType(MediaType.APPLICATION_JSON).content(json))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.type").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.error").value("Invalid ID"));
	}

	@Test
	void resolveMappingEndpoint_resolutionStatusReturned_returnsAddedAndMessage() throws Exception {
		// Arrange
		var dummyStatus = new com.autobuy.web.dto.ResolutionResultStatus(true, "Custom status message");
		when(productResolutionService.resolveMapping("sku123", true)).thenReturn(dummyStatus);

		String json = """
				{
					"externalId": "sku123",
					"saveMapping": true
				}
				""";

		// Act & Assert
		mockMvc.perform(post("/api/autobuy/resolve").contentType(MediaType.APPLICATION_JSON).content(json))
				.andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.added").value(true))
				.andExpect(jsonPath("$.message").value("Custom status message"));
	}

	@Test
	void promoteMappingEndpoint_validId_promotesMappingSuccessfully() throws Exception {
		// Arrange & Act & Assert
		mockMvc.perform(post("/api/mappings/123/promote")).andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true));

		verify(productService).promoteMapping(123L);
	}

	@Test
	void promoteMappingEndpoint_serviceException_returnsInternalServerError() throws Exception {
		// Arrange
		doThrow(new RuntimeException("Promote failed")).when(productService).promoteMapping(123L);

		// Act & Assert
		mockMvc.perform(post("/api/mappings/123/promote")).andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.type").value("INTERNAL_ERROR"))
				.andExpect(jsonPath("$.error").value("Promote failed"));
	}

	@Test
	void demoteMappingEndpoint_validId_demotesMappingSuccessfully() throws Exception {
		// Arrange & Act & Assert
		mockMvc.perform(post("/api/mappings/123/demote")).andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true));

		verify(productService).demoteMapping(123L);
	}

	@Test
	void demoteMappingEndpoint_serviceException_returnsInternalServerError() throws Exception {
		// Arrange
		doThrow(new RuntimeException("Demote failed")).when(productService).demoteMapping(123L);

		// Act & Assert
		mockMvc.perform(post("/api/mappings/123/demote")).andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.type").value("INTERNAL_ERROR"))
				.andExpect(jsonPath("$.error").value("Demote failed"));
	}

	@Test
	void addAlternativeEndpoint_validAlternative_savesMappingWithPriority() throws Exception {
		// Arrange
		when(productService.findMappingsBySearchTextAndSupermarket("apples", "CONTINENTE")).thenReturn(List.of());

		String json = """
				{
					"searchText": "apples",
					"supermarket": "CONTINENTE",
					"externalId": "sku123",
					"productName": "Red Apples"
				}
				""";

		// Act & Assert
		mockMvc.perform(post("/api/mappings/alternative").contentType(MediaType.APPLICATION_JSON).content(json))
				.andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));

		verify(productService).saveMappingWithPriority(eq("apples"), eq("CONTINENTE"), any(), eq(0));
	}

	@Test
	void addAlternativeEndpoint_serviceException_returnsInternalServerError() throws Exception {
		// Arrange
		when(productService.findMappingsBySearchTextAndSupermarket("apples", "CONTINENTE"))
				.thenThrow(new RuntimeException("Database error"));

		String json = """
				{
					"searchText": "apples",
					"supermarket": "CONTINENTE",
					"externalId": "sku123",
					"productName": "Red Apples"
				}
				""";

		// Act & Assert
		mockMvc.perform(post("/api/mappings/alternative").contentType(MediaType.APPLICATION_JSON).content(json))
				.andExpect(status().isInternalServerError()).andExpect(jsonPath("$.type").value("INTERNAL_ERROR"))
				.andExpect(jsonPath("$.error").value("Database error"));
	}

	@Test
	void completeRunEndpoint_validReviewState_completesRun() throws Exception {
		// Arrange & Act & Assert
		mockMvc.perform(post("/api/autobuy/complete")).andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true));

		verify(autoBuyOrchestrationService).completeRun(false);
	}

	@Test
	void completeRunEndpoint_invalidState_returnsConflict() throws Exception {
		// Arrange
		doThrow(new IllegalStateException("Not in review")).when(autoBuyOrchestrationService).completeRun(anyBoolean());

		// Act & Assert
		mockMvc.perform(post("/api/autobuy/complete")).andExpect(status().isConflict())
				.andExpect(jsonPath("$.type").value("STATE_ERROR"))
				.andExpect(jsonPath("$.error").value("Not in review"));
	}

	@Test
	void cancelRunEndpoint_validRun_cancelsRunSuccessfully() throws Exception {
		// Arrange & Act & Assert
		mockMvc.perform(post("/api/autobuy/cancel")).andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true));

		verify(autoBuyOrchestrationService).cancel();
	}

	@Test
	void cancelRunEndpoint_serviceException_returnsInternalServerError() throws Exception {
		// Arrange
		doThrow(new RuntimeException("Cancel failed")).when(autoBuyOrchestrationService).cancel();

		// Act & Assert
		mockMvc.perform(post("/api/autobuy/cancel")).andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.type").value("INTERNAL_ERROR"))
				.andExpect(jsonPath("$.error").value("Cancel failed"));
	}

	@Test
	void saveShoppingList_validList_savesShoppingList() throws Exception {
		// Arrange
		String json = """
				[
					{"query": "Milk", "quantity": 2},
					{"query": "Eggs", "quantity": 12}
				]
				""";

		// Act & Assert
		mockMvc.perform(post("/api/shopping-list").contentType(MediaType.APPLICATION_JSON).content(json))
				.andExpect(status().isOk()).andExpect(jsonPath("$[0].query").value("Milk"))
				.andExpect(jsonPath("$[0].quantity").value(2));
	}

	@Test
	void saveShoppingList_providerException_returnsInternalServerError() throws Exception {
		// Arrange
		doThrow(new RuntimeException("Save error")).when(shoppingListProvider).saveShoppingList(anyString(), anyList());

		String json = "[]";

		// Act & Assert
		mockMvc.perform(post("/api/shopping-list").contentType(MediaType.APPLICATION_JSON).content(json))
				.andExpect(status().isInternalServerError());
	}

	@Test
	void saveCredentials_unchangedInput_returnsCredentialsUnchangedMessage() throws Exception {
		// Arrange
		if (credentialProvider instanceof StubCredentialProvider stub) {
			stub.username = "test-user";
			stub.password = "test-password";
		}

		String json = """
				{
					"supermarket": "CONTINENTE",
					"username": "test-user",
					"password": "test-password"
				}
				""";

		// Act & Assert
		mockMvc.perform(post("/api/credentials").contentType(MediaType.APPLICATION_JSON).content(json))
				.andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
	}

	@Test
	void resolveMapping_blankExternalId_returnsBadRequest() throws Exception {
		// Arrange
		String json = """
				{
					"externalId": "",
					"saveMapping": true
				}
				""";

		// Act & Assert
		mockMvc.perform(post("/api/autobuy/resolve").contentType(MediaType.APPLICATION_JSON).content(json))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.type").value("VALIDATION_ERROR"));
	}

	@Test
	void refineSearch_blankQuery_returnsBadRequest() throws Exception {
		// Arrange
		String json = """
				{
					"query": "   "
				}
				""";

		// Act & Assert
		mockMvc.perform(post("/api/autobuy/refine").contentType(MediaType.APPLICATION_JSON).content(json))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.type").value("VALIDATION_ERROR"));
	}

	@Test
	void addAlternative_blankSearchText_returnsBadRequest() throws Exception {
		// Arrange
		String json = """
				{
					"searchText": "",
					"supermarket": "CONTINENTE",
					"externalId": "123",
					"productName": "Milk"
				}
				""";

		// Act & Assert
		mockMvc.perform(post("/api/mappings/alternative").contentType(MediaType.APPLICATION_JSON).content(json))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.type").value("VALIDATION_ERROR"));
	}

	@Test
	void saveBackupDir_exceptionThrown_returnsBadRequest() throws Exception {
		// Arrange
		if (settingsProvider instanceof StubSettingsProvider stub) {
			stub.throwBackupDirException = true;
		}

		String json = """
				{
					"backupDir": "C:/invalid-backup-dir"
				}
				""";

		// Act & Assert
		mockMvc.perform(post("/api/config/backup-dir").contentType(MediaType.APPLICATION_JSON).content(json))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.type").value("SETTINGS_ERROR"));
	}

	@Test
	void globalExceptionHandler_shoppingListException_returnsBadRequest() throws Exception {
		// Arrange
		when(shoppingListProvider.getShoppingList(anyString()))
				.thenThrow(new ShoppingListException("Invalid shopping list file"));

		// Act & Assert
		mockMvc.perform(get("/api/shopping-list")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.type").value("SHOPPING_LIST_ERROR"))
				.andExpect(jsonPath("$.error").value("Invalid shopping list file"));
	}

	@Test
	void globalExceptionHandler_driverException_returnsBadGateway() throws Exception {
		// Arrange
		when(autoBuyExecutionContext.getState()).thenThrow(new DriverException("Driver failed to initialize"));

		// Act & Assert
		mockMvc.perform(get("/api/autobuy/status")).andExpect(status().isBadGateway())
				.andExpect(jsonPath("$.type").value("DRIVER_ERROR"))
				.andExpect(jsonPath("$.error").value("Driver failed to initialize"));
	}

	@Test
	void globalExceptionHandler_settingsException_returnsBadRequest() throws Exception {
		// Arrange
		when(autoBuyExecutionContext.getState()).thenThrow(new SettingsException("Settings failure"));

		// Act & Assert
		mockMvc.perform(get("/api/autobuy/status")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.type").value("SETTINGS_ERROR"))
				.andExpect(jsonPath("$.error").value("Settings failure"));
	}

	@Test
	void globalExceptionHandler_illegalArgumentException_returnsBadRequest() throws Exception {
		// Arrange
		when(autoBuyExecutionContext.getState()).thenThrow(new IllegalArgumentException("Invalid argument"));

		// Act & Assert
		mockMvc.perform(get("/api/autobuy/status")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.type").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.error").value("Invalid argument"));
	}

	@Test
	void globalExceptionHandler_illegalStateException_returnsConflict() throws Exception {
		// Arrange
		when(autoBuyExecutionContext.getState()).thenThrow(new IllegalStateException("State error"));

		// Act & Assert
		mockMvc.perform(get("/api/autobuy/status")).andExpect(status().isConflict())
				.andExpect(jsonPath("$.type").value("STATE_ERROR")).andExpect(jsonPath("$.error").value("State error"));
	}

	@Test
	void globalExceptionHandler_generalException_returnsInternalServerError() throws Exception {
		// Arrange
		when(autoBuyExecutionContext.getState()).thenThrow(new RuntimeException("General error"));

		// Act & Assert
		mockMvc.perform(get("/api/autobuy/status")).andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.type").value("INTERNAL_ERROR"))
				.andExpect(jsonPath("$.error").value("General error"));
	}

	@Test
	void refineSearch_validQuery_refinesSearchSuccessfully() throws Exception {
		// Arrange
		String json = """
				{
					"query": "red apples"
				}
				""";

		// Act & Assert
		mockMvc.perform(post("/api/autobuy/refine").contentType(MediaType.APPLICATION_JSON).content(json))
				.andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));

		verify(productResolutionService).refineSearch("red apples");
	}

	@Test
	void refineSearch_autoBuyException_returnsBadRequest() throws Exception {
		// Arrange
		String json = """
				{
					"query": "red apples"
				}
				""";

		doThrow(new com.autobuy.exception.AutoBuyException("Refinement rejected")).when(productResolutionService)
				.refineSearch("red apples");

		// Act & Assert
		mockMvc.perform(post("/api/autobuy/refine").contentType(MediaType.APPLICATION_JSON).content(json))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.type").value("AUTOBUY_ERROR"))
				.andExpect(jsonPath("$.error").value("Refinement rejected"));
	}
}
