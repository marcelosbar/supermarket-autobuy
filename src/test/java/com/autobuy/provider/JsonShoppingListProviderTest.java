package com.autobuy.provider;

import com.autobuy.model.ShoppingItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JsonShoppingListProviderTest {

	private final JsonShoppingListProvider provider = new JsonShoppingListProvider(
			new com.fasterxml.jackson.databind.ObjectMapper());

	@Test
	void testGetShoppingList_Success(@TempDir Path tempDir) throws IOException {
		// Arrange
		File tempFile = tempDir.resolve("test-list.json").toFile();
		try (FileWriter writer = new FileWriter(tempFile)) {
			writer.write("""
					[
					  {
					    "query": "Mimosa Meio Gordo",
					    "quantity": 6
					  },
					  {
					    "query": "Bananas",
					    "quantity": 3
					  }
					]
					""");
		}

		// Act
		List<ShoppingItem> items = provider.getShoppingList(tempFile.getAbsolutePath());

		// Assert
		assertNotNull(items);
		assertEquals(2, items.size());

		assertEquals("Mimosa Meio Gordo", items.get(0).query());
		assertEquals(6, items.get(0).quantity());

		assertEquals("Bananas", items.get(1).query());
		assertEquals(3, items.get(1).quantity());
	}

	@Test
	void testGetShoppingList_FileNotFound() {
		// Act
		List<ShoppingItem> items = provider.getShoppingList("non-existent-file.json");

		// Assert
		assertNotNull(items);
		assertTrue(items.isEmpty());
	}

	@Test
	void testGetShoppingList_InvalidJson(@TempDir Path tempDir) throws IOException {
		// Arrange
		File tempFile = tempDir.resolve("invalid-list.json").toFile();
		try (FileWriter writer = new FileWriter(tempFile)) {
			writer.write("{ invalid json }");
		}

		// Act
		List<ShoppingItem> items = provider.getShoppingList(tempFile.getAbsolutePath());

		// Assert
		assertNotNull(items);
		assertTrue(items.isEmpty());
	}
}
