package com.autobuy.web;

import com.autobuy.exception.CredentialException;
import com.autobuy.exception.DriverException;
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

import java.io.IOException;
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
class WebApiControllerIT {

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
	void testGetCredentialsStatus() throws Exception {
		mockMvc.perform(get("/api/credentials?supermarket=CONTINENTE")).andExpect(status().isOk());
	}

	@Test
	void testGetShoppingList() throws Exception {
		mockMvc.perform(get("/api/shopping-list")).andExpect(status().isOk());
	}

	@Test
	void testGetMappings() throws Exception {
		mockMvc.perform(get("/api/mappings")).andExpect(status().isOk());
	}

	@Test
	void testSaveCredentials_Success() throws Exception {
		String json = """
				{
					"supermarket": "CONTINENTE",
					"username": "new-user@example.com",
					"password": "new-password"
				}
				""";

		mockMvc.perform(post("/api/credentials").contentType(MediaType.APPLICATION_JSON).content(json))
				.andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.message").value("Credentials saved successfully."));

		if (credentialProvider instanceof StubCredentialProvider stub) {
			org.junit.jupiter.api.Assertions.assertEquals("new-user@example.com", stub.username);
			org.junit.jupiter.api.Assertions.assertEquals("new-password", stub.password);
		}
	}

	@Test
	void testSaveCredentials_ValidationError() throws Exception {
		if (credentialProvider instanceof StubCredentialProvider stub) {
			stub.throwCredentialException = true;
			stub.throwMessage = "Validation failed: username cannot be empty";
		}

		String json = """
				{
					"supermarket": "CONTINENTE",
					"username": "",
					"password": "new-password"
				}
				""";

		mockMvc.perform(post("/api/credentials").contentType(MediaType.APPLICATION_JSON).content(json))
				.andExpect(status().isInternalServerError()).andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.message")
						.value("Failed to save credentials: Validation failed: username cannot be empty"));
	}

	@Test
	void testSaveCredentials_UnsupportedOperation() throws Exception {
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

		mockMvc.perform(post("/api/credentials").contentType(MediaType.APPLICATION_JSON).content(json))
				.andExpect(status().isInternalServerError()).andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.message")
						.value("Database/properties credentials saving not supported in this profile."));
	}

	@Test
	void testGetBackupDir() throws Exception {
		mockMvc.perform(get("/api/config/backup-dir")).andExpect(status().isOk())
				.andExpect(jsonPath("$.backupDir").exists());
	}

	@Test
	void testSaveBackupDir() throws Exception {
		String json = """
				{
					"backupDir": "C:/mock-backup"
				}
				""";

		mockMvc.perform(post("/api/config/backup-dir").contentType(MediaType.APPLICATION_JSON).content(json))
				.andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
	}

	@Test
	void testGetBackupStatus() throws Exception {
		mockMvc.perform(get("/api/config/backup-status")).andExpect(status().isOk())
				.andExpect(jsonPath("$.backupDir").exists()).andExpect(jsonPath("$.isConfigured").exists());
	}

	@Test
	void testGetBackupStatus_NotConfigured() throws Exception {
		if (settingsProvider instanceof StubSettingsProvider stub) {
			stub.backupDir = null;
		}
		try {
			mockMvc.perform(get("/api/config/backup-status")).andExpect(status().isOk())
					.andExpect(jsonPath("$.backupDir").value("")).andExpect(jsonPath("$.isConfigured").value(false));
		} finally {
			if (settingsProvider instanceof StubSettingsProvider stub) {
				stub.backupDir = "C:/mock-backup";
			}
		}
	}

	@Test
	void testGetCredentialsStatus_NullUsernameOrPassword() throws Exception {
		if (credentialProvider instanceof StubCredentialProvider stub) {
			stub.username = null;
			stub.password = null;
		}
		try {
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
	void testGetCredentialsStatus_BlankUsernameOrPassword() throws Exception {
		if (credentialProvider instanceof StubCredentialProvider stub) {
			stub.username = "   ";
			stub.password = "   ";
		}
		try {
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
	void testSaveCredentials_UnchangedPasswordOmitted() throws Exception {
		if (credentialProvider instanceof StubCredentialProvider stub) {
			stub.username = "existing-user";
			stub.password = "existing-pass";
		}
		try {
			String json = """
					{
						"supermarket": "CONTINENTE",
						"username": "existing-user",
						"password": ""
					}
					""";
			mockMvc.perform(post("/api/credentials").contentType(MediaType.APPLICATION_JSON).content(json))
					.andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
					.andExpect(jsonPath("$.message").value("Credentials unchanged."));
		} finally {
			if (credentialProvider instanceof StubCredentialProvider stub) {
				stub.username = "test-user";
				stub.password = "test-password";
			}
		}
	}

	@Test
	void testSaveBackupDir_EmptyPath() throws Exception {
		String json = """
				{
					"backupDir": ""
				}
				""";
		mockMvc.perform(post("/api/config/backup-dir").contentType(MediaType.APPLICATION_JSON).content(json))
				.andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
	}

	@Test
	void testSaveBackupDir_ProviderException() throws Exception {
		if (settingsProvider instanceof StubSettingsProvider stub) {
			stub.throwBackupDirException = true;
		}
		try {
			String json = """
					{
						"backupDir": "C:/mock-backup"
					}
					""";
			mockMvc.perform(post("/api/config/backup-dir").contentType(MediaType.APPLICATION_JSON).content(json))
					.andExpect(status().isInternalServerError()).andExpect(jsonPath("$.success").value(false))
					.andExpect(jsonPath("$.message")
							.value(org.hamcrest.Matchers.containsString("Failed to save backup directory")));
		} finally {
			if (settingsProvider instanceof StubSettingsProvider stub) {
				stub.throwBackupDirException = false;
			}
		}
	}

	@Test
	void testSelectNativeDirectory_Success() throws Exception {
		when(folderPicker.selectDirectory()).thenReturn("/custom/backup/dir");

		mockMvc.perform(post("/api/config/select-native-dir")).andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.path").value("/custom/backup/dir"));
	}

	@Test
	void testSelectNativeDirectory_Cancelled() throws Exception {
		when(folderPicker.selectDirectory()).thenReturn(null);

		mockMvc.perform(post("/api/config/select-native-dir")).andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.message").value("Selection cancelled"));
	}

	@Test
	void testSelectNativeDirectory_Exception() throws Exception {
		when(folderPicker.selectDirectory()).thenThrow(new RuntimeException("Drive not ready"));

		mockMvc.perform(post("/api/config/select-native-dir")).andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.message").value("Error opening native directory chooser: Drive not ready"));
	}

	@Test
	void testSelectNativeDirectory_Headless() throws Exception {
		when(folderPicker.selectDirectory()).thenThrow(new UnsupportedOperationException(
				"Cannot open native folder picker: Graphics environment is headless."));

		mockMvc.perform(post("/api/config/select-native-dir")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success").value(false)).andExpect(jsonPath("$.message")
						.value("Cannot open native folder picker: Graphics environment is headless."));
	}

	@Test
	void testSearchSupermarket_Success() throws Exception {
		var dummyResult = new com.autobuy.model.SearchResult("sku", "Product", "Brand", java.math.BigDecimal.TEN, "url",
				"cat");
		when(guestSearchService.performGuestSearch("milk", "CONTINENTE")).thenReturn(List.of(dummyResult));

		mockMvc.perform(get("/api/autobuy/search").param("query", "milk").param("supermarket", "CONTINENTE"))
				.andExpect(status().isOk()).andExpect(jsonPath("$[0].externalId").value("sku"))
				.andExpect(jsonPath("$[0].name").value("Product"));
	}

	@Test
	void testSearchSupermarket_Failure() throws Exception {
		when(guestSearchService.performGuestSearch("milk", "CONTINENTE"))
				.thenThrow(new RuntimeException("Search failed"));

		mockMvc.perform(get("/api/autobuy/search").param("query", "milk").param("supermarket", "CONTINENTE"))
				.andExpect(status().isInternalServerError());
	}

	@Test
	void testShutdown() throws Exception {
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
		public void saveBackupDir(String backupDir) throws IOException {
			if (throwBackupDirException) {
				throw new IOException("Failed to save backup dir");
			}
			this.backupDir = backupDir;
		}
	}

	// 5. Additional Endpoints Coverage Tests

	@Test
	void testDeleteMapping_NotFound() throws Exception {
		mockMvc.perform(delete("/api/mappings/9999")).andExpect(status().isNotFound());
	}

	@Test
	void testDeleteMapping_Success() throws Exception {
		ProductMapping mapping = new ProductMapping("query-del", "CONTINENTE", "sku-del", "Product Delete");
		when(productService.findMappingById(1L)).thenReturn(Optional.of(mapping));

		mockMvc.perform(delete("/api/mappings/1")).andExpect(status().isNoContent());

		verify(productService).deleteMapping(1L);
	}

	@Test
	void testRunAutoBuy_Success() throws Exception {
		String json = """
				{
					"supermarket": "CONTINENTE"
				}
				""";

		mockMvc.perform(post("/api/autobuy/run").contentType(MediaType.APPLICATION_JSON).content(json))
				.andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.message").value("Auto-Buy started successfully."));

		verify(autoBuyOrchestrationService).startAutoBuy("shopping-list.json", "CONTINENTE", false);
	}

	@Test
	void testRunAutoBuy_IllegalState() throws Exception {
		doThrow(new IllegalStateException("Already running")).when(autoBuyOrchestrationService)
				.startAutoBuy("shopping-list.json", "CONTINENTE", false);

		String json = """
				{
					"supermarket": "CONTINENTE"
				}
				""";

		mockMvc.perform(post("/api/autobuy/run").contentType(MediaType.APPLICATION_JSON).content(json))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.message").value("Already running"));
	}

	@Test
	void testGetAutoBuyStatus() throws Exception {
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

		mockMvc.perform(get("/api/autobuy/status")).andExpect(status().isOk())
				.andExpect(jsonPath("$.state").value("RUNNING"))
				.andExpect(jsonPath("$.currentItemQuery").value("query"))
				.andExpect(jsonPath("$.currentItemQuantity").value(5))
				.andExpect(jsonPath("$.searchResults[0].externalId").value("sku"))
				.andExpect(jsonPath("$.logs[0]").value("INFO - log line"));
	}

	@Test
	void testResolveMappingEndpoint_Success() throws Exception {
		String json = """
				{
					"externalId": "sku123",
					"saveMapping": true
				}
				""";

		mockMvc.perform(post("/api/autobuy/resolve").contentType(MediaType.APPLICATION_JSON).content(json))
				.andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));

		verify(productResolutionService).resolveMapping("sku123", true);
	}

	@Test
	void testResolveMappingEndpoint_Failure() throws Exception {
		doThrow(new IllegalArgumentException("Invalid ID")).when(productResolutionService)
				.resolveMapping(eq("invalid-sku"), anyBoolean());

		String json = """
				{
					"externalId": "invalid-sku",
					"saveMapping": false
				}
				""";

		mockMvc.perform(post("/api/autobuy/resolve").contentType(MediaType.APPLICATION_JSON).content(json))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.message").value("Invalid ID"));
	}

	@Test
	void testResolveMappingEndpoint_SuccessWithStatus() throws Exception {
		var dummyStatus = new com.autobuy.web.dto.ResolutionResultStatus(true, "Custom status message");
		when(productResolutionService.resolveMapping("sku123", true)).thenReturn(dummyStatus);

		String json = """
				{
					"externalId": "sku123",
					"saveMapping": true
				}
				""";

		mockMvc.perform(post("/api/autobuy/resolve").contentType(MediaType.APPLICATION_JSON).content(json))
				.andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.added").value(true))
				.andExpect(jsonPath("$.message").value("Custom status message"));
	}

	@Test
	void testPromoteMappingEndpoint_Success() throws Exception {
		mockMvc.perform(post("/api/mappings/123/promote")).andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true));

		verify(productService).promoteMapping(123L);
	}

	@Test
	void testPromoteMappingEndpoint_Failure() throws Exception {
		doThrow(new RuntimeException("Promote failed")).when(productService).promoteMapping(123L);

		mockMvc.perform(post("/api/mappings/123/promote")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success").value(false)).andExpect(jsonPath("$.message").value("Promote failed"));
	}

	@Test
	void testDemoteMappingEndpoint_Success() throws Exception {
		mockMvc.perform(post("/api/mappings/123/demote")).andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true));

		verify(productService).demoteMapping(123L);
	}

	@Test
	void testDemoteMappingEndpoint_Failure() throws Exception {
		doThrow(new RuntimeException("Demote failed")).when(productService).demoteMapping(123L);

		mockMvc.perform(post("/api/mappings/123/demote")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success").value(false)).andExpect(jsonPath("$.message").value("Demote failed"));
	}

	@Test
	void testAddAlternativeEndpoint_Success() throws Exception {
		when(productService.findMappingsBySearchTextAndSupermarket("apples", "CONTINENTE")).thenReturn(List.of());

		String json = """
				{
					"searchText": "apples",
					"supermarket": "CONTINENTE",
					"externalId": "sku123",
					"productName": "Red Apples"
				}
				""";

		mockMvc.perform(post("/api/mappings/alternative").contentType(MediaType.APPLICATION_JSON).content(json))
				.andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));

		verify(productService).saveMappingWithPriority(eq("apples"), eq("CONTINENTE"), any(), eq(0));
	}

	@Test
	void testAddAlternativeEndpoint_Failure() throws Exception {
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

		mockMvc.perform(post("/api/mappings/alternative").contentType(MediaType.APPLICATION_JSON).content(json))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.message").value("Database error"));
	}

	@Test
	void testCompleteRunEndpoint_Success() throws Exception {
		mockMvc.perform(post("/api/autobuy/complete")).andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true));

		verify(autoBuyOrchestrationService).completeRun(false);
	}

	@Test
	void testCompleteRunEndpoint_Failure() throws Exception {
		doThrow(new IllegalStateException("Not in review")).when(autoBuyOrchestrationService).completeRun(anyBoolean());

		mockMvc.perform(post("/api/autobuy/complete")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success").value(false)).andExpect(jsonPath("$.message").value("Not in review"));
	}

	@Test
	void testCancelRunEndpoint_Success() throws Exception {
		mockMvc.perform(post("/api/autobuy/cancel")).andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true));

		verify(autoBuyOrchestrationService).cancel();
	}

	@Test
	void testCancelRunEndpoint_Failure() throws Exception {
		doThrow(new RuntimeException("Cancel failed")).when(autoBuyOrchestrationService).cancel();

		mockMvc.perform(post("/api/autobuy/cancel")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success").value(false)).andExpect(jsonPath("$.message").value("Cancel failed"));
	}

	@Test
	void testSaveShoppingList_Success() throws Exception {
		String json = """
				[
					{"query": "Milk", "quantity": 2},
					{"query": "Eggs", "quantity": 12}
				]
				""";
		mockMvc.perform(post("/api/shopping-list").contentType(MediaType.APPLICATION_JSON).content(json))
				.andExpect(status().isOk()).andExpect(jsonPath("$[0].query").value("Milk"))
				.andExpect(jsonPath("$[0].quantity").value(2));
	}

	@Test
	void testSaveShoppingList_Exception() throws Exception {
		doThrow(new RuntimeException("Save error")).when(shoppingListProvider).saveShoppingList(anyString(), anyList());

		String json = "[]";
		mockMvc.perform(post("/api/shopping-list").contentType(MediaType.APPLICATION_JSON).content(json))
				.andExpect(status().isInternalServerError());
	}

	@Test
	void testSaveCredentials_Unchanged() throws Exception {
		if (credentialProvider instanceof StubCredentialProvider stub) {
			stub.username = "test-user";
			stub.password = "test-password";
		}

		String json = """
				{
					"supermarket": "CONTINENTE",
					"username": "test-user",
					"password": ""
				}
				""";

		mockMvc.perform(post("/api/credentials").contentType(MediaType.APPLICATION_JSON).content(json))
				.andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.message").value("Credentials unchanged."));
	}

	@Test
	void testSaveBackupDir_Exception() throws Exception {
		if (settingsProvider instanceof StubSettingsProvider stub) {
			stub.throwBackupDirException = true;
		}

		String json = """
				{
					"backupDir": "C:/invalid-backup-dir"
				}
				""";
		mockMvc.perform(post("/api/config/backup-dir").contentType(MediaType.APPLICATION_JSON).content(json))
				.andExpect(status().isInternalServerError()).andExpect(jsonPath("$.success").value(false));
	}

	@Test
	void testGlobalExceptionHandler_ShoppingListException() throws Exception {
		when(shoppingListProvider.getShoppingList(anyString()))
				.thenThrow(new ShoppingListException("Invalid shopping list file"));

		mockMvc.perform(get("/api/shopping-list")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.type").value("SHOPPING_LIST_ERROR"))
				.andExpect(jsonPath("$.error").value("Invalid shopping list file"));
	}

	@Test
	void testGlobalExceptionHandler_DriverException() throws Exception {
		when(autoBuyExecutionContext.getState()).thenThrow(new DriverException("Driver failed to initialize"));

		mockMvc.perform(get("/api/autobuy/status")).andExpect(status().isBadGateway())
				.andExpect(jsonPath("$.type").value("DRIVER_ERROR"))
				.andExpect(jsonPath("$.error").value("Driver failed to initialize"));
	}

	@Test
	void testGlobalExceptionHandler_IllegalArgumentException() throws Exception {
		when(autoBuyExecutionContext.getState()).thenThrow(new IllegalArgumentException("Invalid argument"));

		mockMvc.perform(get("/api/autobuy/status")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.type").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.error").value("Invalid argument"));
	}

	@Test
	void testGlobalExceptionHandler_IllegalStateException() throws Exception {
		when(autoBuyExecutionContext.getState()).thenThrow(new IllegalStateException("State error"));

		mockMvc.perform(get("/api/autobuy/status")).andExpect(status().isConflict())
				.andExpect(jsonPath("$.type").value("STATE_ERROR")).andExpect(jsonPath("$.error").value("State error"));
	}

	@Test
	void testGlobalExceptionHandler_GeneralException() throws Exception {
		when(autoBuyExecutionContext.getState()).thenThrow(new RuntimeException("General error"));

		mockMvc.perform(get("/api/autobuy/status")).andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.type").value("INTERNAL_ERROR"))
				.andExpect(jsonPath("$.error").value("General error"));
	}

	@Test
	void testRefineSearch_Success() throws Exception {
		String json = """
				{
					"query": "red apples"
				}
				""";

		mockMvc.perform(post("/api/autobuy/refine").contentType(MediaType.APPLICATION_JSON).content(json))
				.andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));

		verify(productResolutionService).refineSearch("red apples");
	}

	@Test
	void testRefineSearch_Failure() throws Exception {
		String json = """
				{
					"query": "red apples"
				}
				""";

		doThrow(new com.autobuy.exception.AutoBuyException("Refinement rejected")).when(productResolutionService)
				.refineSearch("red apples");

		mockMvc.perform(post("/api/autobuy/refine").contentType(MediaType.APPLICATION_JSON).content(json))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.message").value("Refinement rejected"));
	}
}
