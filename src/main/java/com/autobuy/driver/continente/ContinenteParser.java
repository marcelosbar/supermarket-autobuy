package com.autobuy.driver.continente;

import com.autobuy.model.SearchResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Locator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Parser utility for extracting product information and pricing from Continente
 * web elements.
 */
final class ContinenteParser {

	private static final Logger log = LoggerFactory.getLogger(ContinenteParser.class);
	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	private ContinenteParser() {
		// Prevent instantiation
	}

	/**
	 * Parses a single product tile element and adds the resulting SearchResult to
	 * the results list.
	 *
	 * @param tile
	 *            the product tile locator
	 * @param results
	 *            the target list for parsed SearchResults
	 */
	public static void parseSingleProductTile(Locator tile, List<SearchResult> results) {
		try {
			String externalId = extractExternalId(tile);
			Locator titleLink = tile.locator(ContinenteSelectors.PRODUCT_TITLE_LINK).first();
			String name = extractProductName(titleLink);
			String url = extractProductUrl(titleLink, tile);
			String brand = extractProductBrand(tile, name);
			BigDecimal priceValue = extractProductPrice(tile, name);
			String category = "Supermercado";

			// Determine availability from elements on the page
			Locator plusBtn = tile.locator(ContinenteSelectors.INCREASE_QTY_BUTTON).first();
			Locator qtyInput = tile.locator(ContinenteSelectors.QTY_INPUT).first();
			Locator addBtn = tile.locator(ContinenteSelectors.ADD_TO_CART_BUTTON).first();
			boolean available = plusBtn.isVisible() || qtyInput.isVisible()
					|| (addBtn.isVisible() && addBtn.isEnabled());

			if (externalId != null && !externalId.isBlank() && !name.isBlank()) {
				results.add(new SearchResult(externalId, name, brand, priceValue, url, category, available));
			}
		} catch (Exception e) {
			log.warn("Failed to parse single product tile: {}", e.getMessage());
		}
	}

	/**
	 * Extracts the external product ID (SKU) from the product tile.
	 */
	public static String extractExternalId(Locator tile) {
		String externalId = tile.getAttribute("data-pid");
		if (externalId == null || externalId.isBlank()) {
			String idAttr = tile.getAttribute("id");
			if (idAttr != null) {
				externalId = idAttr.replaceAll(ContinenteSelectors.NON_DIGIT_REGEX, "");
			}
		}
		return externalId;
	}

	/**
	 * Extracts the product name from the title link element.
	 */
	public static String extractProductName(Locator titleLink) {
		if (titleLink.count() > 0) {
			return titleLink.innerText().trim();
		}
		return "";
	}

	/**
	 * Extracts the product detail page URL from the title link element and tile
	 * context.
	 */
	public static String extractProductUrl(Locator titleLink, Locator tile) {
		String href = findHref(titleLink, tile);
		if (href == null || href.isBlank()) {
			return ContinenteSelectors.BASE_URL;
		}
		if (!href.startsWith("http")) {
			return ContinenteSelectors.BASE_URL + href;
		}
		return href;
	}

	private static String findHref(Locator titleLink, Locator tile) {
		if (titleLink.count() == 0) {
			return null;
		}
		String href = titleLink.getAttribute("href");
		if (href != null && !href.isBlank()) {
			return href;
		}
		Locator anchor = titleLink.locator("a").first();
		if (anchor.count() > 0) {
			return anchor.getAttribute("href");
		}
		anchor = tile.locator("a[href]").first();
		if (anchor.count() > 0) {
			return anchor.getAttribute("href");
		}
		return null;
	}

	/**
	 * Extracts the product brand from the tile, checking data layers first, falling
	 * back to the first word of the name.
	 */
	public static String extractProductBrand(Locator tile, String name) {
		Locator productTile = tile.locator(".product-tile").first();
		if (productTile.count() > 0) {
			String brand = parseBrandFromDataLayer(productTile.getAttribute("data-product-tile-impression"));
			if (brand != null) {
				return brand;
			}
		}

		Locator brandLoc = tile.locator(ContinenteSelectors.PRODUCT_BRAND).first();
		if (brandLoc.count() > 0 && brandLoc.isVisible()) {
			return brandLoc.innerText().trim();
		}
		String[] words = name.split(" ");
		if (words.length > 0) {
			return words[0];
		}
		return "Generico";
	}

	private static String parseBrandFromDataLayer(String dataLayer) {
		if (dataLayer == null || dataLayer.isBlank()) {
			return null;
		}
		try {
			JsonNode node = OBJECT_MAPPER.readTree(dataLayer);
			if (node.has("brand")) {
				String brand = node.get("brand").asText();
				if (brand != null && !brand.isBlank()) {
					return brand.trim();
				}
			}
		} catch (Exception e) {
			log.warn("Failed to parse brand from data-product-tile-impression attribute: {}", e.getMessage());
		}
		return null;
	}

	/**
	 * Extracts and parses the product price from the tile.
	 */
	public static BigDecimal extractProductPrice(Locator tile, String name) {
		Locator primaryLoc = tile.locator(ContinenteSelectors.PRICE_PRIMARY).first();
		if (primaryLoc.count() == 0) {
			primaryLoc = tile.locator(ContinenteSelectors.PRICE_FALLBACK).first();
		}
		Locator secondaryLoc = tile.locator(ContinenteSelectors.PRICE_SECONDARY).first();

		String primaryText = (primaryLoc.count() > 0) ? primaryLoc.innerText().trim() : "";
		String secondaryText = (secondaryLoc.count() > 0) ? secondaryLoc.innerText().trim() : "";
		String chosenPriceText = primaryText;

		if (!primaryText.isEmpty() && !secondaryText.isEmpty()) {
			boolean primaryIsUnit = primaryText.contains("/");
			boolean secondaryIsUnit = secondaryText.contains("/");
			if (primaryIsUnit && !secondaryIsUnit) {
				log.info("Detected price inversion for '{}': Primary='{}', Secondary='{}'. Using Secondary.", name,
						primaryText, secondaryText);
				chosenPriceText = secondaryText;
			}
		}

		if (!chosenPriceText.isEmpty()) {
			return parsePrice(chosenPriceText);
		}
		return BigDecimal.ZERO;
	}

	/**
	 * Parses pricing strings (e.g. "1,49 €", "€ 10.99") into a BigDecimal.
	 */
	public static BigDecimal parsePrice(String priceText) {
		if (priceText == null || priceText.isBlank()) {
			return BigDecimal.ZERO;
		}
		try {
			// Keep only digits, dots, and commas (strips out ?, newlines, currency symbols,
			// and units)
			String cleaned = priceText.replaceAll("[^0-9.,]", "");

			// Determine if comma is decimal separator (e.g. 1,49)
			if (cleaned.contains(",") && cleaned.contains(".")) {
				// Format: 1.250,45 -> remove dots, replace comma with dot
				cleaned = cleaned.replace(".", "").replace(",", ".");
			} else if (cleaned.contains(",")) {
				// Format: 1,49 -> replace comma with dot
				cleaned = cleaned.replace(",", ".");
			}

			// Prepend a zero if it starts with a decimal dot (e.g. .89 -> 0.89)
			if (cleaned.startsWith(".")) {
				cleaned = "0" + cleaned;
			}

			return new BigDecimal(cleaned).setScale(2, RoundingMode.HALF_UP);
		} catch (Exception e) {
			log.warn("Failed to parse price string '{}': {}", priceText, e.getMessage());
			return BigDecimal.ZERO;
		}
	}
}
