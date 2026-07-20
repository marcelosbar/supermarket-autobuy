package com.autobuy.provider;

import com.autobuy.exception.ShoppingListException;
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
		// Act & Assert
		assertThrows(ShoppingListException.class, () -> provider.getShoppingList("non-existent-file.json"));
	}

	@Test
	void testGetShoppingList_InvalidJson(@TempDir Path tempDir) throws IOException {
		// Arrange
		File tempFile = tempDir.resolve("invalid-list.json").toFile();
		try (FileWriter writer = new FileWriter(tempFile)) {
			writer.write("{ invalid json }");
		}

		// Act & Assert
		assertThrows(ShoppingListException.class, () -> provider.getShoppingList(tempFile.getAbsolutePath()));
	}

	@Test
	void testSaveShoppingList_Success(@TempDir java.nio.file.Path tempDir) {
		// Arrange
		File tempFile = tempDir.resolve("save-list.json").toFile();
		List<ShoppingItem> itemsToSave = List.of(new ShoppingItem("Milk", 2), new ShoppingItem("Eggs", 12));

		// Act
		provider.saveShoppingList(tempFile.getAbsolutePath(), itemsToSave);

		// Assert
		assertTrue(tempFile.exists());
		List<ShoppingItem> loadedItems = provider.getShoppingList(tempFile.getAbsolutePath());
		assertEquals(2, loadedItems.size());
		assertEquals("Milk", loadedItems.get(0).query());
		assertEquals(2, loadedItems.get(0).quantity());
		assertEquals("Eggs", loadedItems.get(1).query());
		assertEquals(12, loadedItems.get(1).quantity());
	}

	@Test
	void testSaveShoppingList_IOException() {
		// Saving to a directory path should trigger IOException and
		// ShoppingListException
		assertThrows(com.autobuy.exception.ShoppingListException.class, () -> {
			provider.saveShoppingList("target/", List.of());
		});
	}
}
