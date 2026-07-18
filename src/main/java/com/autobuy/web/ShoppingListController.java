package com.autobuy.web;

import com.autobuy.model.ShoppingItem;
import com.autobuy.provider.ShoppingListProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller exposing shopping list endpoints.
 */
@RestController
@RequestMapping("/shopping-list")
public class ShoppingListController {

	private static final Logger log = LoggerFactory.getLogger(ShoppingListController.class);

	private final ShoppingListProvider shoppingListProvider;
	private static final String DEFAULT_LIST_PATH = "shopping-list.json";

	public ShoppingListController(ShoppingListProvider shoppingListProvider) {
		this.shoppingListProvider = shoppingListProvider;
	}

	@GetMapping
	public ResponseEntity<List<ShoppingItem>> getShoppingList() {
		List<ShoppingItem> items = shoppingListProvider.getShoppingList(DEFAULT_LIST_PATH);
		return ResponseEntity.ok(items);
	}

	@PostMapping
	public ResponseEntity<List<ShoppingItem>> saveShoppingList(@RequestBody List<ShoppingItem> items) {
		try {
			shoppingListProvider.saveShoppingList(DEFAULT_LIST_PATH, items);
			return ResponseEntity.ok(items);
		} catch (Exception e) {
			log.error("Failed to save shopping list via provider", e);
			return ResponseEntity.internalServerError().build();
		}
	}
}
