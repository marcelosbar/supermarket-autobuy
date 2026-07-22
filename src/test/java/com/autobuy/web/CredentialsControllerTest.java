package com.autobuy.web;

import com.autobuy.provider.CredentialProvider;
import com.autobuy.web.dto.ActionResponse;
import com.autobuy.web.dto.CredentialStatusResponse;
import com.autobuy.web.dto.CredentialsRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CredentialsControllerTest {

	@Test
	void testGetCredentialsStatus_NullUsernameNullPassword() {
		CredentialProvider provider = mock(CredentialProvider.class);
		when(provider.getUsername(anyString())).thenReturn(null);
		when(provider.getPassword(anyString())).thenReturn(null);

		CredentialsController controller = new CredentialsController(provider);
		ResponseEntity<CredentialStatusResponse> response = controller.getCredentialsStatus("CONTINENTE");

		CredentialStatusResponse body = response.getBody();
		assertNotNull(body);
		assertEquals("CONTINENTE", body.supermarket());
		assertFalse(body.hasUsername());
		assertFalse(body.hasPassword());
		assertEquals("", body.username());
	}

	@Test
	void testGetCredentialsStatus_BlankUsernameBlankPassword() {
		CredentialProvider provider = mock(CredentialProvider.class);
		when(provider.getUsername(anyString())).thenReturn("   ");
		when(provider.getPassword(anyString())).thenReturn("   ");

		CredentialsController controller = new CredentialsController(provider);
		ResponseEntity<CredentialStatusResponse> response = controller.getCredentialsStatus("CONTINENTE");

		CredentialStatusResponse body = response.getBody();
		assertNotNull(body);
		assertFalse(body.hasUsername());
		assertFalse(body.hasPassword());
		assertEquals("   ", body.username());
	}

	@Test
	void testSaveCredentials_UnchangedButRequestUsernameNull() {
		CredentialProvider provider = mock(CredentialProvider.class);
		when(provider.getUsername("CONTINENTE")).thenReturn("user");
		when(provider.getPassword("CONTINENTE")).thenReturn("pass");

		CredentialsController controller = new CredentialsController(provider);
		CredentialsRequest request = new CredentialsRequest("CONTINENTE", null, "");

		ResponseEntity<ActionResponse> response = controller.saveCredentials(request);
		assertNotNull(response.getBody());
		assertTrue(response.getBody().success());
		verify(provider).saveCredentials("CONTINENTE", null, "");
	}

	@Test
	void testSaveCredentials_UnchangedPasswordNull() {
		CredentialProvider provider = mock(CredentialProvider.class);
		when(provider.getUsername("CONTINENTE")).thenReturn("user");
		when(provider.getPassword("CONTINENTE")).thenReturn("pass");

		CredentialsController controller = new CredentialsController(provider);
		CredentialsRequest request = new CredentialsRequest("CONTINENTE", "user", null);

		ResponseEntity<ActionResponse> response = controller.saveCredentials(request);
		assertNotNull(response.getBody());
		assertTrue(response.getBody().success());
		assertEquals("Credentials unchanged.", response.getBody().message());
		verify(provider, never()).saveCredentials(any(), any(), any());
	}

	@Test
	void testSaveCredentials_NoExistingCredentials() {
		CredentialProvider provider = mock(CredentialProvider.class);
		when(provider.getUsername("CONTINENTE")).thenReturn(null);
		when(provider.getPassword("CONTINENTE")).thenReturn(null);

		CredentialsController controller = new CredentialsController(provider);
		CredentialsRequest request = new CredentialsRequest("CONTINENTE", "user", "pass");

		ResponseEntity<ActionResponse> response = controller.saveCredentials(request);
		assertNotNull(response.getBody());
		assertTrue(response.getBody().success());
		verify(provider).saveCredentials("CONTINENTE", "user", "pass");
	}

	@Test
	void testSaveCredentials_UsernameChanged() {
		CredentialProvider provider = mock(CredentialProvider.class);
		when(provider.getUsername("CONTINENTE")).thenReturn("user");
		when(provider.getPassword("CONTINENTE")).thenReturn("pass");

		CredentialsController controller = new CredentialsController(provider);
		CredentialsRequest request = new CredentialsRequest("CONTINENTE", "different-user", "");

		ResponseEntity<ActionResponse> response = controller.saveCredentials(request);
		assertNotNull(response.getBody());
		assertTrue(response.getBody().success());
		verify(provider).saveCredentials("CONTINENTE", "different-user", "");
	}
}
