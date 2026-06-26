package com.autobuy.provider;

import com.autobuy.model.ShoppingItem;
import java.util.List;

/**
 * Interface for loading shopping lists.
 */
public interface ShoppingListProvider {
	/**
	 * Retrieves the shopping list from the specified source.
	 *
	 * @param sourcePath
	 *            The path or URI of the source list (e.g. "shopping-list.json")
	 * @return List of shopping items
	 */
	List<ShoppingItem> getShoppingList(String sourcePath);
}
