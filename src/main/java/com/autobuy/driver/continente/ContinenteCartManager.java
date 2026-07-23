package com.autobuy.driver.continente;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

/**
 * Handles cart management actions (clearing, adding, updating quantity) for
 * Continente Online.
 */
class ContinenteCartManager {

	private static final Logger log = LoggerFactory.getLogger(ContinenteCartManager.class);

	private final Page page;
	private final Consumer<String> searchCallback;

	/**
	 * Constructs a new ContinenteCartManager.
	 *
	 * @param page
	 *            the active Playwright page
	 * @param searchCallback
	 *            callback to trigger a product search if product not visible on
	 *            screen
	 */
	public ContinenteCartManager(Page page, Consumer<String> searchCallback) {
		this.page = page;
		this.searchCallback = searchCallback;
	}

	/**
	 * Checks if the cart needs to be cleared, and if so, runs API or UI-based
	 * clearing.
	 */
	public void clearCart() {
		log.info("Checking if cart needs to be cleared...");
		try {
			int count = getCartQuantity();

			if (count == 0) {
				log.info("Cart is already empty.");
				return;
			}

			log.info("Cart has {} items. Initiating cart clearing...", count);

			// Step 1: Execute direct API call to remove all items
			if (clearCartViaApi()) {
				return;
			}

			// Step 2: Fallback — Navigate to Cart Page and clear
			clearCartViaCartPage();
		} catch (Exception e) {
			log.error("Failed to clear cart: {}", e.getMessage(), e);
		}
	}

	private int getCartQuantity() {
		try {
			Locator badges = page.locator(ContinenteSelectors.MINICART_QUANTITY);
			int totalBadges = badges.count();
			for (int i = 0; i < totalBadges; i++) {
				String text = badges.nth(i).textContent();
				if (text != null && !text.isBlank()) {
					String digits = text.replaceAll(ContinenteSelectors.NON_DIGIT_REGEX, "");
					if (!digits.isEmpty()) {
						int val = Integer.parseInt(digits);
						if (val > 0) {
							return val;
						}
					}
				}
			}
		} catch (Exception e) {
			log.debug("Error reading minicart quantity: {}", e.getMessage());
		}
		return 0;
	}

	private boolean clearCartViaApi() {
		log.info("Attempting direct API call to clear cart...");
		try {
			page.evaluate(
					"""
							async () => {
							  try {
							    await fetch('/on/demandware.store/Sites-continente-Site/default/Cart-RemoveAllProductLineItems');
							  } catch (e) {}
							  try {
							    await fetch('/on/demandware.store/Sites-continente-Site/default/Cart-RemoveAllProductLineItems', { method: 'POST' });
							  } catch (e) {}
							}
							""");
			log.info("Direct API call finished. Reloading page to update minicart...");
			page.navigate(ContinenteSelectors.BASE_URL);
			page.waitForLoadState();

			int remainingCount = getCartQuantity();
			if (remainingCount == 0) {
				log.info("Cart cleared successfully via direct API call.");
				return true;
			}
			log.info("Cart still has {} items after API call.", remainingCount);
		} catch (Exception e) {
			log.warn("Direct API call to clear cart encountered an error: {}", e.getMessage());
		}
		return false;
	}

	private boolean clearCartViaCartPage() {
		log.info("Navigating to Cart page to clear cart...");
		try {
			page.navigate(ContinenteSelectors.CART_URL);
			page.waitForLoadState();

			Locator cleanBtn = page.locator(
					"button.minicart-clean-button, button.cart-clean-button, button:has-text('Limpar carrinho'), button:has-text('Limpar todos'), button:has-text('Esvaziar')")
					.first();
			if (cleanBtn.isVisible()) {
				cleanBtn.click();
				page.waitForTimeout(800);
				Locator confirmBtn = page.locator(ContinenteSelectors.CONFIRM_CLEAN_MODAL_BUTTON).first();
				if (confirmBtn.isVisible()) {
					confirmBtn.click();
					page.waitForTimeout(1500);
				}
				log.info("Cleared cart on Cart page.");
			}
			page.navigate(ContinenteSelectors.BASE_URL);
			page.waitForLoadState();
			return getCartQuantity() == 0;
		} catch (Exception e) {
			log.warn("Cart page cleanup encountered an error: {}", e.getMessage());
			return false;
		}
	}

	/**
	 * Adds a product SKU to the shopping cart, increasing quantity if already
	 * present.
	 */
	public boolean addProductToCart(String externalId, int quantity) {
		log.info("Adding product SKU {} (quantity={}) to cart...", externalId, quantity);
		try {
			int initialCartQty = getCartQuantity();

			Locator tile = locateProductTile(externalId);
			if (tile == null) {
				return false;
			}

			tile.scrollIntoViewIfNeeded();

			Locator plusBtn = tile.locator(ContinenteSelectors.INCREASE_QTY_BUTTON).first();
			Locator qtyInput = tile.locator(ContinenteSelectors.QTY_INPUT).first();
			Locator qtyDisplay = tile.locator(ContinenteSelectors.QTY_DISPLAY).first();

			int currentQty = getExistingQuantity(qtyInput, qtyDisplay, plusBtn, externalId);

			if (currentQty >= quantity) {
				log.info("Product SKU {} already has quantity {} (target is {}). Confirmed in cart.", externalId,
						currentQty, quantity);
				return true;
			}

			if (currentQty == 0) {
				if (!performInitialAddToCart(tile, plusBtn, externalId)) {
					return false;
				}
				currentQty = 1;
			}

			if (quantity > currentQty) {
				adjustProductQuantity(qtyInput, plusBtn, qtyDisplay, currentQty, quantity);
			}

			return verifyAdditionConfirmed(plusBtn, qtyInput, qtyDisplay, externalId, initialCartQty, quantity);
		} catch (Exception e) {
			log.error("Failed to add SKU {} to cart: {}", externalId, e.getMessage());
			return false;
		}
	}

	private Locator locateProductTile(String externalId) {
		Locator tile = page.locator(String.format(ContinenteSelectors.PRODUCT_TILE_BY_PID, externalId, externalId))
				.first();
		if (!tile.isVisible()) {
			searchCallback.accept(externalId);
			tile = page.locator(String.format(ContinenteSelectors.PRODUCT_TILE_BY_PID, externalId, externalId)).first();
		}
		if (!tile.isVisible()) {
			log.error("Product tile with SKU {} not found on page.", externalId);
			return null;
		}
		return tile;
	}

	private boolean performInitialAddToCart(Locator tile, Locator plusBtn, String externalId) {
		Locator addBtn = tile.locator(ContinenteSelectors.ADD_TO_CART_BUTTON).first();
		if (!addBtn.isVisible() || addBtn.getAttribute("class").contains("disabled")) {
			log.error("Add to cart button not visible or is disabled for product SKU {}", externalId);
			return false;
		}

		addBtn.click();

		boolean updated = waitForFirstCartAddition(plusBtn, tile, addBtn, externalId);
		if (!updated) {
			log.error("Failed to add SKU {} to cart (plus button did not appear).", externalId);
			return false;
		}
		return true;
	}

	private boolean verifyAdditionConfirmed(Locator plusBtn, Locator qtyInput, Locator qtyDisplay, String externalId,
			int initialCartQty, int quantity) {
		if (plusBtn.isVisible() || getExistingQuantity(qtyInput, qtyDisplay, plusBtn, externalId) > 0) {
			log.info("Successfully added SKU {} (quantity={}) to cart (confirmed via tile controls).", externalId,
					quantity);
			return true;
		}

		for (int attempt = 0; attempt < 25; attempt++) {
			if (plusBtn.isVisible() || getExistingQuantity(qtyInput, qtyDisplay, plusBtn, externalId) > 0) {
				log.info("Successfully added SKU {} to cart (confirmed via tile controls after wait).", externalId);
				return true;
			}
			if (getCartQuantity() > initialCartQty) {
				log.info("Successfully added SKU {} to cart (confirmed via minicart badge).", externalId);
				return true;
			}
			page.waitForTimeout(200);
		}

		if (plusBtn.isVisible() || getExistingQuantity(qtyInput, qtyDisplay, plusBtn, externalId) > 0) {
			log.info("Successfully added SKU {} to cart (confirmed via final tile check).", externalId);
			return true;
		}

		log.error("Failed to add SKU {} to cart: item addition not confirmed on tile or minicart.", externalId);
		return false;
	}

	private boolean waitForFirstCartAddition(Locator plusBtn, Locator tile, Locator addBtn, String externalId) {
		for (int attempt = 0; attempt < 25; attempt++) {
			if (plusBtn.isVisible()) {
				return true;
			}
			if (checkProductIsOutOfStock(tile, addBtn, externalId)) {
				break;
			}
			page.waitForTimeout(100);
		}
		return false;
	}

	private boolean checkProductIsOutOfStock(Locator tile, Locator addBtn, String externalId) {
		try {
			String tileClass = tile.getAttribute("class");
			if (tileClass != null && tileClass.contains("ct-product-tile-out-of-stock")) {
				log.warn("Detecting that product SKU {} tile became out-of-stock.", externalId);
				return true;
			}

			Locator unavailableBadge = tile.locator(".dual-badge-unavailable-message").first();
			if (unavailableBadge.isVisible()) {
				log.warn("Detecting that product SKU {} unavailable badge appeared.", externalId);
				return true;
			}

			String outOfStockAttr = addBtn.getAttribute("data-outofstock");
			if (outOfStockAttr != null && "true".equalsIgnoreCase(outOfStockAttr.trim())) {
				log.warn("Detecting that product SKU {} add button has data-outofstock=true.", externalId);
				return true;
			}
		} catch (Exception e) {
			log.debug("Ignore element detached errors during state transition: {}", e.getMessage());
		}
		return false;
	}

	private int getExistingQuantity(Locator qtyInput, Locator qtyDisplay, Locator plusBtn, String externalId) {
		int currentQty = 0;
		// A plus button visible means it's already in the cart
		if (plusBtn.isVisible()) {
			String qtyText = readRawQuantityText(qtyInput, qtyDisplay);
			try {
				currentQty = Integer.parseInt(qtyText.replaceAll(ContinenteSelectors.NON_DIGIT_REGEX, ""));
				log.info("Product SKU {} is already in cart. Current quantity: {}", externalId, currentQty);
			} catch (Exception e) {
				log.warn("Could not parse current quantity for SKU {}, assuming 0: {}", externalId, e.getMessage());
				currentQty = 0;
			}
		}
		return currentQty;
	}

	private String readRawQuantityText(Locator qtyInput, Locator qtyDisplay) {
		String qtyText = "";
		if (qtyInput.count() > 0) {
			qtyText = qtyInput.getAttribute(ContinenteSelectors.VALUE_ATTR);
			if (qtyText == null || qtyText.isEmpty()) {
				qtyText = qtyInput.getAttribute(ContinenteSelectors.ARIA_VALUENOW_ATTR);
			}
			if (qtyText == null || qtyText.isEmpty()) {
				qtyText = qtyInput.innerText().trim();
			}
		}
		if ((qtyText == null || qtyText.isEmpty()) && qtyDisplay.count() > 0) {
			qtyText = qtyDisplay.innerText().trim();
		}
		return qtyText;
	}

	private void adjustProductQuantity(Locator qtyInput, Locator plusBtn, Locator qtyDisplay, int currentQty,
			int quantity) {
		boolean directInputSuccess = tryDirectQuantityInput(qtyInput, quantity);

		// Fallback to clicking plus button in a loop
		if (!directInputSuccess) {
			clickIncrementLoop(plusBtn, qtyInput, qtyDisplay, currentQty, quantity);
		}
	}

	private boolean tryDirectQuantityInput(Locator qtyInput, int quantity) {
		try {
			qtyInput.waitFor(new Locator.WaitForOptions().setTimeout(2000));
			if (qtyInput.isVisible() && qtyInput.isEnabled()) {
				qtyInput.click();
				qtyInput.fill(String.valueOf(quantity));
				qtyInput.press("Enter");
				log.info("Directly set quantity to {} via text input.", quantity);

				return verifyDirectInputUpdate(qtyInput, quantity);
			}
		} catch (Exception e) {
			log.info("Could not set quantity directly via input: {}. Falling back to click loop...", e.getMessage());
		}
		return false;
	}

	private boolean verifyDirectInputUpdate(Locator qtyInput, int quantity) {
		for (int attempt = 0; attempt < 10; attempt++) {
			page.waitForTimeout(100);
			String val = qtyInput.getAttribute(ContinenteSelectors.VALUE_ATTR);
			if (val == null || val.isEmpty()) {
				val = qtyInput.getAttribute(ContinenteSelectors.ARIA_VALUENOW_ATTR);
			}
			try {
				if (Integer.parseInt(val.replaceAll(ContinenteSelectors.NON_DIGIT_REGEX, "")) == quantity) {
					return true;
				}
			} catch (Exception _) {
				// Ignore parse errors during verification
			}
		}
		return false;
	}

	private void clickIncrementLoop(Locator plusBtn, Locator qtyInput, Locator qtyDisplay, int currentQty,
			int quantity) {
		try {
			plusBtn.waitFor(new Locator.WaitForOptions().setTimeout(5000));
			for (int i = currentQty; i < quantity; i++) {
				plusBtn.waitFor(new Locator.WaitForOptions().setTimeout(3000));
				plusBtn.click();

				// Wait up to 2 seconds for the UI text or input value to update to (i + 1)
				boolean updated = waitForQuantityUpdate(qtyInput, qtyDisplay, i + 1);
				if (!updated) {
					// Fallback: sleep to let the server process the request
					page.waitForTimeout(800);
				}
			}
		} catch (Exception e) {
			log.error("Click-loop adjustment failed: {}", e.getMessage());
		}
	}

	private boolean waitForQuantityUpdate(Locator qtyInput, Locator qtyDisplay, int targetVal) {
		for (int attempt = 0; attempt < 20; attempt++) {
			page.waitForTimeout(100);
			String currentText = readRawQuantityText(qtyInput, qtyDisplay);
			try {
				int val = Integer.parseInt(currentText.replaceAll(ContinenteSelectors.NON_DIGIT_REGEX, ""));
				if (val == targetVal) {
					return true;
				}
			} catch (Exception _) {
				// Ignore parse errors
			}
		}
		return false;
	}
}
