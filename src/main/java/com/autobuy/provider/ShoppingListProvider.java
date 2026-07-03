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

	/**
	 * Saves the shopping list to the specified source.
	 *
	 * @param sourcePath
	 *            The path or URI of the source list (e.g. "shopping-list.json")
	 * @param items
	 *            List of shopping items to save
	 * @throws UnsupportedOperationException
	 *             If saving credentials is not supported by this provider
	 */
	default void saveShoppingList(String sourcePath, List<ShoppingItem> items) {
		throw new UnsupportedOperationException("Saving shopping list is not supported by this provider.");
	}
}
