package com.autobuy.model;

/**
 * Represents an item in the shopping list to be automated.
 *
 * @param query
 *            The search text or query used to find the product
 * @param quantity
 *            The amount to purchase
 */
public record ShoppingItem(String query, int quantity) {
}
