package com.autobuy.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.autobuy.exception.CredentialException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Integration tests for {@link GlobalExceptionHandler}.
 *
 * <p>
 * A minimal {@link TestRestController} is registered via
 * {@link TestConfiguration} to provide endpoints that deliberately throw
 * exceptions, allowing end-to-end validation of the handler mappings without
 * depending on production controller behaviour.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GlobalExceptionHandlerIT {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void credentialException_returns400WithCredentialErrorType() throws Exception {
		// Arrange & Act & Assert
		mockMvc.perform(get("/api/test/credential-error")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.type").value("CREDENTIAL_ERROR"));
	}

	@Test
	void illegalStateException_returns409WithStateErrorType() throws Exception {
		// Arrange & Act & Assert
		mockMvc.perform(get("/api/test/state-error")).andExpect(status().isConflict())
				.andExpect(jsonPath("$.type").value("STATE_ERROR"));
	}

	/**
	 * Minimal controller that deliberately throws exceptions to exercise the
	 * handler.
	 */
	@RestController
	@RequestMapping("/test")
	static class TestRestController {

		@GetMapping("/credential-error")
		public void throwCredentialException() {
			throw new CredentialException("test credential error");
		}

		@GetMapping("/state-error")
		public void throwIllegalStateException() {
			throw new IllegalStateException("test state error");
		}
	}

	/**
	 * Registers {@link TestRestController} as a Spring bean for the test context.
	 */
	@TestConfiguration
	static class TestConfig {

		@Bean
		public TestRestController testRestController() {
			return new TestRestController();
		}
	}
}
