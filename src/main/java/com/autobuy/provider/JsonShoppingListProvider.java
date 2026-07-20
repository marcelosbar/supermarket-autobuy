package com.autobuy.provider;

import com.autobuy.model.ShoppingItem;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.autobuy.exception.ShoppingListException;
import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Implementation of ShoppingListProvider that reads from a local JSON file.
 */
@Component
public class JsonShoppingListProvider implements ShoppingListProvider {

	private static final Logger log = LoggerFactory.getLogger(JsonShoppingListProvider.class);

	private final ObjectMapper objectMapper;

	public JsonShoppingListProvider(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	@Override
	public List<ShoppingItem> getShoppingList(String sourcePath) {
		File file = new File(sourcePath);
		if (!file.exists()) {
			log.error("Shopping list file not found: {}", sourcePath);
			throw new ShoppingListException("Shopping list file not found: " + sourcePath);
		}

		try {
			List<ShoppingItem> items = objectMapper.readValue(file, new TypeReference<List<ShoppingItem>>() {
			});
			log.info("Loaded {} items from shopping list: {}", items.size(), sourcePath);
			return items;
		} catch (IOException e) {
			log.error("Failed to parse shopping list file: {}", sourcePath, e);
			throw new ShoppingListException("Failed to parse shopping list file: " + sourcePath, e);
		}
	}

	@Override
	public void saveShoppingList(String sourcePath, List<ShoppingItem> items) {
		try {
			objectMapper.writeValue(new File(sourcePath), items);
			log.info("Saved updated shopping list to {}", sourcePath);
		} catch (IOException e) {
			log.error("Failed to write shopping list file: {}", sourcePath, e);
			throw new ShoppingListException("Failed to save shopping list to " + sourcePath, e);
		}
	}
}
