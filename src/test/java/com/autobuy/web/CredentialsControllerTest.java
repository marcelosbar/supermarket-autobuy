package com.autobuy.web;

import com.autobuy.provider.CredentialProvider;
import com.autobuy.web.dto.ActionResponse;
import com.autobuy.web.dto.CredentialStatusResponse;
import com.autobuy.web.dto.CredentialsRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CredentialsControllerTest {

	@Mock
	private CredentialProvider provider;

	@InjectMocks
	private CredentialsController controller;

	@Test
	void getCredentialsStatus_nullCredentials_returnsHasFlagsFalse() {
		// Arrange
		when(provider.getUsername(anyString())).thenReturn(null);
		when(provider.getPassword(anyString())).thenReturn(null);

		// Act
		ResponseEntity<CredentialStatusResponse> response = controller.getCredentialsStatus("CONTINENTE");

		// Assert
		CredentialStatusResponse body = response.getBody();
		assertNotNull(body);
		assertEquals("CONTINENTE", body.supermarket());
		assertFalse(body.hasUsername());
		assertFalse(body.hasPassword());
		assertEquals("", body.username());
	}

	@Test
	void getCredentialsStatus_blankCredentials_returnsHasFlagsFalse() {
		// Arrange
		when(provider.getUsername(anyString())).thenReturn("   ");
		when(provider.getPassword(anyString())).thenReturn("   ");

		// Act
		ResponseEntity<CredentialStatusResponse> response = controller.getCredentialsStatus("CONTINENTE");

		// Assert
		CredentialStatusResponse body = response.getBody();
		assertNotNull(body);
		assertFalse(body.hasUsername());
		assertFalse(body.hasPassword());
		assertEquals("   ", body.username());
	}

	@Test
	void saveCredentials_newUsername_savesCredentials() {
		// Arrange
		when(provider.getUsername("CONTINENTE")).thenReturn("user");
		when(provider.getPassword("CONTINENTE")).thenReturn("pass");

		CredentialsRequest request = new CredentialsRequest("CONTINENTE", "new-user", "pass");

		// Act
		ResponseEntity<ActionResponse> response = controller.saveCredentials(request);

		// Assert
		assertNotNull(response.getBody());
		assertTrue(response.getBody().success());
		verify(provider).saveCredentials("CONTINENTE", "new-user", "pass");
	}

	@Test
	void saveCredentials_noExistingCredentials_savesNewCredentials() {
		// Arrange
		when(provider.getUsername("CONTINENTE")).thenReturn(null);
		when(provider.getPassword("CONTINENTE")).thenReturn(null);

		CredentialsRequest request = new CredentialsRequest("CONTINENTE", "user", "pass");

		// Act
		ResponseEntity<ActionResponse> response = controller.saveCredentials(request);

		// Assert
		assertNotNull(response.getBody());
		assertTrue(response.getBody().success());
		verify(provider).saveCredentials("CONTINENTE", "user", "pass");
	}

	@Test
	void saveCredentials_usernameChanged_savesUpdatedCredentials() {
		// Arrange
		when(provider.getUsername("CONTINENTE")).thenReturn("user");
		when(provider.getPassword("CONTINENTE")).thenReturn("pass");

		CredentialsRequest request = new CredentialsRequest("CONTINENTE", "different-user", "new-pass");

		// Act
		ResponseEntity<ActionResponse> response = controller.saveCredentials(request);

		// Assert
		assertNotNull(response.getBody());
		assertTrue(response.getBody().success());
		verify(provider).saveCredentials("CONTINENTE", "different-user", "new-pass");
	}
}
