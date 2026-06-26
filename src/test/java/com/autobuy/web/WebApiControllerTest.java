package com.autobuy.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WebApiControllerTest {

	@Autowired
	private MockMvc mockMvc;

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
}
