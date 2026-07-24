package com.autobuy.model;

import java.math.BigDecimal;

/**
 * Represents a product search result from a supermarket store.
 *
 * @param externalId
 *            The supermarket-specific unique identifier/SKU
 * @param name
 *            The product name
 * @param brand
 *            The product brand
 * @param price
 *            The current unit price
 * @param url
 *            The product details URL
 * @param category
 *            The category of the product
 */
public record SearchResult(String externalId, String name, String brand, BigDecimal price, String url, String category,
		boolean available, String quantity, String unitPrice) {

	public SearchResult(String externalId, String name, String brand, BigDecimal price, String url, String category) {
		this(externalId, name, brand, price, url, category, true, null, null);
	}

	public SearchResult(String externalId, String name, String brand, BigDecimal price, String url, String category,
			boolean available) {
		this(externalId, name, brand, price, url, category, available, null, null);
	}
}
