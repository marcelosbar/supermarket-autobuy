package com.autobuy.provider;

import com.autobuy.model.ShoppingItem;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * Implementation of ShoppingListProvider that reads from a local JSON file.
 */
@Component
public class JsonShoppingListProvider implements ShoppingListProvider {

	private static final Logger log = LoggerFactory.getLogger(JsonShoppingListProvider.class);

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Override
	public List<ShoppingItem> getShoppingList(String sourcePath) {
		File file = new File(sourcePath);
		if (!file.exists()) {
			log.error("Shopping list file not found: {}", sourcePath);
			return Collections.emptyList();
		}

		try {
			List<ShoppingItem> items = objectMapper.readValue(file, new TypeReference<List<ShoppingItem>>() {
			});
			log.info("Loaded {} items from shopping list: {}", items.size(), sourcePath);
			return items;
		} catch (IOException e) {
			log.error("Failed to parse shopping list file: {}", sourcePath, e);
			return Collections.emptyList();
		}
	}
}
