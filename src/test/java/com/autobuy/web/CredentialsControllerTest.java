package com.autobuy.web;

import com.autobuy.provider.CredentialProvider;
import com.autobuy.web.dto.CredentialsRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CredentialsControllerTest {

	@Test
	void testGetCredentialsStatus_NullUsernameNullPassword() {
		CredentialProvider provider = mock(CredentialProvider.class);
		when(provider.getUsername(anyString())).thenReturn(null);
		when(provider.getPassword(anyString())).thenReturn(null);

		CredentialsController controller = new CredentialsController(provider);
		ResponseEntity<Map<String, Object>> response = controller.getCredentialsStatus("CONTINENTE");

		Map<String, Object> body = response.getBody();
		assertEquals("CONTINENTE", body.get("supermarket"));
		assertFalse((Boolean) body.get("hasUsername"));
		assertFalse((Boolean) body.get("hasPassword"));
		assertEquals("", body.get("username"));
	}

	@Test
	void testGetCredentialsStatus_BlankUsernameBlankPassword() {
		CredentialProvider provider = mock(CredentialProvider.class);
		when(provider.getUsername(anyString())).thenReturn("   ");
		when(provider.getPassword(anyString())).thenReturn("   ");

		CredentialsController controller = new CredentialsController(provider);
		ResponseEntity<Map<String, Object>> response = controller.getCredentialsStatus("CONTINENTE");

		Map<String, Object> body = response.getBody();
		assertFalse((Boolean) body.get("hasUsername"));
		assertFalse((Boolean) body.get("hasPassword"));
		assertEquals("   ", body.get("username"));
	}

	@Test
	void testSaveCredentials_UnchangedButRequestUsernameNull() {
		CredentialProvider provider = mock(CredentialProvider.class);
		when(provider.getUsername("CONTINENTE")).thenReturn("user");
		when(provider.getPassword("CONTINENTE")).thenReturn("pass");

		CredentialsController controller = new CredentialsController(provider);
		CredentialsRequest request = new CredentialsRequest("CONTINENTE", null, "");

		ResponseEntity<Map<String, Object>> response = controller.saveCredentials(request);
		assertTrue((Boolean) response.getBody().get("success"));
		verify(provider).saveCredentials("CONTINENTE", null, "");
	}

	@Test
	void testSaveCredentials_UnchangedPasswordNull() {
		CredentialProvider provider = mock(CredentialProvider.class);
		when(provider.getUsername("CONTINENTE")).thenReturn("user");
		when(provider.getPassword("CONTINENTE")).thenReturn("pass");

		CredentialsController controller = new CredentialsController(provider);
		CredentialsRequest request = new CredentialsRequest("CONTINENTE", "user", null);

		ResponseEntity<Map<String, Object>> response = controller.saveCredentials(request);
		assertTrue((Boolean) response.getBody().get("success"));
		assertEquals("Credentials unchanged.", response.getBody().get("message"));
		verify(provider, never()).saveCredentials(any(), any(), any());
	}

	@Test
	void testSaveCredentials_NoExistingCredentials() {
		CredentialProvider provider = mock(CredentialProvider.class);
		when(provider.getUsername("CONTINENTE")).thenReturn(null);
		when(provider.getPassword("CONTINENTE")).thenReturn(null);

		CredentialsController controller = new CredentialsController(provider);
		CredentialsRequest request = new CredentialsRequest("CONTINENTE", "user", "pass");

		ResponseEntity<Map<String, Object>> response = controller.saveCredentials(request);
		assertTrue((Boolean) response.getBody().get("success"));
		verify(provider).saveCredentials("CONTINENTE", "user", "pass");
	}

	@Test
	void testSaveCredentials_UsernameChanged() {
		CredentialProvider provider = mock(CredentialProvider.class);
		when(provider.getUsername("CONTINENTE")).thenReturn("user");
		when(provider.getPassword("CONTINENTE")).thenReturn("pass");

		CredentialsController controller = new CredentialsController(provider);
		CredentialsRequest request = new CredentialsRequest("CONTINENTE", "different-user", "");

		ResponseEntity<Map<String, Object>> response = controller.saveCredentials(request);
		assertTrue((Boolean) response.getBody().get("success"));
		verify(provider).saveCredentials("CONTINENTE", "different-user", "");
	}
}
