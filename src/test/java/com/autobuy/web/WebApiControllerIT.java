package com.autobuy.web;

import com.autobuy.exception.CredentialException;
import com.autobuy.provider.CredentialProvider;
import com.autobuy.provider.SettingsProvider;
import com.autobuy.web.dto.AutoBuyStatusResponse;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import org.springframework.boot.test.mock.mockito.MockBean;
import com.autobuy.service.ShutdownService;
import com.autobuy.service.DatabaseBackupService;

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

	@MockBean
	private ShutdownService shutdownService;

	@MockBean
	private DatabaseBackupService databaseBackupService;

	@MockBean
	private AutoBuyWebService autoBuyWebService;

	@Autowired
	private com.autobuy.service.ProductService productService;

	@BeforeEach
	void setUp() {
		if (credentialProvider instanceof StubCredentialProvider stub) {
			stub.username = "test-user";
			stub.password = "test-password";
			stub.throwUnsupported = false;
			stub.throwCredentialException = false;
			stub.throwMessage = "";
		}
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
	void testSelectNativeDirectory_Headless() throws Exception {
		mockMvc.perform(post("/api/config/select-native-dir")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success").value(false)).andExpect(jsonPath("$.message")
						.value("Cannot open native folder picker: Graphics environment is headless."));
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
		public void saveBackupDir(String backupDir) {
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
		var searchResult = new com.autobuy.model.SearchResult("sku-del", "Product Delete", "Brand",
				java.math.BigDecimal.ONE, "url", "cat");
		productService.saveMapping("query-del", "CONTINENTE", searchResult);

		var mappings = productService.findAllMappings();
		long id = mappings.stream().filter(m -> m.getSearchText().equals("query-del")).findFirst()
				.orElseThrow(() -> new AssertionError("Mapping not found")).getId();

		mockMvc.perform(delete("/api/mappings/" + id)).andExpect(status().isNoContent());

		assertTrue(productService.findMappingById(id).isEmpty());
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
				List.of(dummyResult), List.of("log line"), "");

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
					"externalId": "sku123"
				}
				""";

		mockMvc.perform(post("/api/autobuy/resolve").contentType(MediaType.APPLICATION_JSON).content(json))
				.andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));

		org.mockito.Mockito.verify(autoBuyWebService).resolveMapping("sku123");
	}

	@Test
	void testResolveMappingEndpoint_Failure() throws Exception {
		org.mockito.Mockito.doThrow(new IllegalArgumentException("Invalid ID")).when(autoBuyWebService)
				.resolveMapping("invalid-sku");

		String json = """
				{
					"externalId": "invalid-sku"
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

		org.mockito.Mockito.verify(autoBuyWebService).completeRun();
	}

	@Test
	void testCompleteRunEndpoint_Failure() throws Exception {
		org.mockito.Mockito.doThrow(new IllegalStateException("Not in review")).when(autoBuyWebService).completeRun();

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
}
