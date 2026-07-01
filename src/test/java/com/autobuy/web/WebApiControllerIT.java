package com.autobuy.web;

import com.autobuy.exception.CredentialException;
import com.autobuy.provider.CredentialProvider;
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

	@TestConfiguration
	static class TestConfig {
		@Bean
		@Primary
		public CredentialProvider credentialProvider() {
			return new StubCredentialProvider();
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
}
