package com.autobuy.web;

import com.autobuy.exception.CredentialException;
import com.autobuy.exception.DriverException;
import com.autobuy.exception.ShoppingListException;
import com.autobuy.provider.CredentialProvider;
import com.autobuy.provider.SettingsProvider;
import com.autobuy.provider.ShoppingListProvider;
import com.autobuy.service.ShutdownService;
import com.autobuy.service.DatabaseBackupService;
import com.autobuy.model.ProductMapping;
import com.autobuy.web.dto.AutoBuyStatusResponse;
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

	@MockitoBean
	private ShutdownService shutdownService;

	@MockitoBean
	private DatabaseBackupService databaseBackupService;

	@MockitoBean
	private AutoBuyWebService autoBuyWebService;

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
			stub.throwBackupDirException = false;
		}
		when(shoppingListProvider.getShoppingList(anyString())).thenReturn(List.of());
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
		mockMvc.perform(get("/api/autobuy/backup-status")).andExpect(status().isOk())
				.andExpect(jsonPath("$.backupDir").exists()).andExpect(jsonPath("$.isConfigured").exists());
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
		when(autoBuyWebService.performGuestSearch("milk", "CONTINENTE")).thenReturn(List.of(dummyResult));

		mockMvc.perform(get("/api/autobuy/search").param("query", "milk").param("supermarket", "CONTINENTE"))
				.andExpect(status().isOk()).andExpect(jsonPath("$[0].externalId").value("sku"))
				.andExpect(jsonPath("$[0].name").value("Product"));
	}

	@Test
	void testSearchSupermarket_Failure() throws Exception {
		when(autoBuyWebService.performGuestSearch("milk", "CONTINENTE"))
				.thenThrow(new RuntimeException("Search failed"));

		mockMvc.perform(get("/api/autobuy/search").param("query", "milk").param("supermarket", "CONTINENTE"))
				.andExpect(status().isInternalServerError());
	}

	@Test
	void testShutdown() throws Exception {
		mockMvc.perform(post("/api/shutdown")).andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.message")
						.value("Application is shutting down gracefully. Backup will be created."));

		org.mockito.Mockito.verify(shutdownService).initiateShutdown(1000);
	}

	@TestConfiguration
	static class TestConfig {
		@Bean
		@Primary
		public StubCredentialProvider stubCredentialProvider() {
			return new StubCredentialProvider();
		}
	}

	private static class StubCredentialProvider implements CredentialProvider, SettingsProvider {
		private String username = "test-user";
		private String password = "test-password";
		private String backupDir = "C:/mock-backup";
		private boolean throwUnsupported = false;
		private boolean throwCredentialException = false;
		private String throwMessage = "";
		private boolean throwBackupDirException = false;

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

		org.mockito.Mockito.verify(autoBuyWebService).startAutoBuy("shopping-list.json", "CONTINENTE", false);
	}

	@Test
	void testRunAutoBuy_IllegalState() throws Exception {
		org.mockito.Mockito.doThrow(new IllegalStateException("Already running")).when(autoBuyWebService)
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
		var dummyStatus = new AutoBuyStatusResponse(AutoBuyWebService.AutoBuyState.RUNNING, "query", 5,
				List.of(dummyResult), List.of("log line"), "", List.of(), List.of());

		org.mockito.Mockito.when(autoBuyWebService.getStatus()).thenReturn(dummyStatus);

		mockMvc.perform(get("/api/autobuy/status")).andExpect(status().isOk())
				.andExpect(jsonPath("$.state").value("RUNNING"))
				.andExpect(jsonPath("$.currentItemQuery").value("query"))
				.andExpect(jsonPath("$.currentItemQuantity").value(5))
				.andExpect(jsonPath("$.searchResults[0].externalId").value("sku"))
				.andExpect(jsonPath("$.logs[0]").value("log line"));
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

		org.mockito.Mockito.verify(autoBuyWebService).resolveMapping("sku123", true);
	}

	@Test
	void testResolveMappingEndpoint_Failure() throws Exception {
		org.mockito.Mockito.doThrow(new IllegalArgumentException("Invalid ID")).when(autoBuyWebService)
				.resolveMapping(org.mockito.Mockito.eq("invalid-sku"), org.mockito.Mockito.anyBoolean());

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
	void testCompleteRunEndpoint_Success() throws Exception {
		mockMvc.perform(post("/api/autobuy/complete")).andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true));

		org.mockito.Mockito.verify(autoBuyWebService).completeRun(false);
	}

	@Test
	void testCompleteRunEndpoint_Failure() throws Exception {
		org.mockito.Mockito.doThrow(new IllegalStateException("Not in review")).when(autoBuyWebService)
				.completeRun(anyBoolean());

		mockMvc.perform(post("/api/autobuy/complete")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success").value(false)).andExpect(jsonPath("$.message").value("Not in review"));
	}

	@Test
	void testCancelRunEndpoint_Success() throws Exception {
		mockMvc.perform(post("/api/autobuy/cancel")).andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true));

		org.mockito.Mockito.verify(autoBuyWebService).cancel();
	}

	@Test
	void testCancelRunEndpoint_Failure() throws Exception {
		org.mockito.Mockito.doThrow(new RuntimeException("Cancel failed")).when(autoBuyWebService).cancel();

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
		if (credentialProvider instanceof StubCredentialProvider stub) {
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
		when(autoBuyWebService.getStatus()).thenThrow(new DriverException("Driver failed to initialize"));

		mockMvc.perform(get("/api/autobuy/status")).andExpect(status().isBadGateway())
				.andExpect(jsonPath("$.type").value("DRIVER_ERROR"))
				.andExpect(jsonPath("$.error").value("Driver failed to initialize"));
	}

	@Test
	void testGlobalExceptionHandler_IllegalArgumentException() throws Exception {
		when(autoBuyWebService.getStatus()).thenThrow(new IllegalArgumentException("Invalid argument"));

		mockMvc.perform(get("/api/autobuy/status")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.type").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.error").value("Invalid argument"));
	}

	@Test
	void testGlobalExceptionHandler_IllegalStateException() throws Exception {
		when(autoBuyWebService.getStatus()).thenThrow(new IllegalStateException("State error"));

		mockMvc.perform(get("/api/autobuy/status")).andExpect(status().isConflict())
				.andExpect(jsonPath("$.type").value("STATE_ERROR")).andExpect(jsonPath("$.error").value("State error"));
	}

	@Test
	void testGlobalExceptionHandler_GeneralException() throws Exception {
		when(autoBuyWebService.getStatus()).thenThrow(new RuntimeException("General error"));

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

		verify(autoBuyWebService).refineSearch("red apples");
	}

	@Test
	void testRefineSearch_Failure() throws Exception {
		String json = """
				{
					"query": "red apples"
				}
				""";

		doThrow(new com.autobuy.exception.AutoBuyException("Refinement rejected")).when(autoBuyWebService)
				.refineSearch("red apples");

		mockMvc.perform(post("/api/autobuy/refine").contentType(MediaType.APPLICATION_JSON).content(json))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.message").value("Refinement rejected"));
	}
}
