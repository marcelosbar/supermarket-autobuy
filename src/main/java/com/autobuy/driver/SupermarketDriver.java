package com.autobuy.driver;

import com.autobuy.model.SearchResult;
import java.util.List;

/**
 * Interface defining the automation actions for a supermarket store.
 */
public interface SupermarketDriver {

	/**
	 * Gets the unique name of this supermarket driver.
	 *
	 * @return Supermarket identifier (e.g., "CONTINENTE")
	 */
	String getSupermarketName();

	/**
	 * Initializes the driver, launches the browser context, and logs into the
	 * store.
	 *
	 * @param username
	 *            The store login username
	 * @param password
	 *            The store login password
	 * @param headful
	 *            Whether to display the browser window
	 */
	void initialize(String username, String password, boolean headful);

	/**
	 * Searches for a product by query string and returns top candidates.
	 *
	 * @param query
	 *            The search query
	 * @return List of matching search results
	 */
	List<SearchResult> searchProduct(String query);

	/**
	 * Adds a product to the shopping cart.
	 *
	 * @param externalId
	 *            The supermarket-specific product ID/SKU
	 * @param quantity
	 *            The quantity to add
	 * @return true if added successfully, false otherwise
	 */
	boolean addProductToCart(String externalId, int quantity);

	/**
	 * Checks if a product is available (e.g. in stock, purchasable) without adding
	 * it.
	 *
	 * @param externalId
	 *            The supermarket-specific product ID/SKU
	 * @return true if the product is available, false otherwise
	 */
	boolean isProductAvailable(String externalId);

	/**
	 * Closes browser resources and clean up sessions.
	 */
	void close();

	/**
	 * Navigates to the shopping cart page.
	 */
	void navigateToCart();
}
