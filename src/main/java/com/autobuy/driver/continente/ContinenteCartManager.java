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
			Locator qtyBadge = page.locator(ContinenteSelectors.MINICART_QUANTITY).first();
			int count = getCartQuantity(qtyBadge);

			if (count == 0) {
				log.info("Cart is already empty.");
				return;
			}

			log.info("Cart has {} items. Initiating cart clearing...", count);

			// Method 1: Direct AJAX API Call (GET & POST)
			if (clearCartViaApi(qtyBadge)) {
				return;
			}

			// Method 2: UI-based hover and Clear All button click
			clearCartViaUi(qtyBadge);

		} catch (Exception e) {
			log.error("Failed to clear cart: {}", e.getMessage(), e);
		}
	}

	private int getCartQuantity(Locator qtyBadge) {
		try {
			qtyBadge.waitFor(new Locator.WaitForOptions().setTimeout(5000));
			String qtyText = qtyBadge.innerText().trim();
			return Integer.parseInt(qtyText.replaceAll(ContinenteSelectors.NON_DIGIT_REGEX, ""));
		} catch (Exception _) {
			log.info("Minicart quantity badge not visible after 5s. Cart is likely empty.");
			return 0;
		}
	}

	private boolean clearCartViaApi(Locator qtyBadge) {
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
			waitForNetworkIdle(4000);

			int remainingCount = getCartQuantityFallback(qtyBadge);
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

	private int getCartQuantityFallback(Locator qtyBadge) {
		try {
			if (qtyBadge.isVisible()) {
				String text = qtyBadge.innerText().trim();
				return Integer.parseInt(text.replaceAll(ContinenteSelectors.NON_DIGIT_REGEX, ""));
			}
			// Wait up to 3 seconds for the badge to become visible if it has items
			qtyBadge.waitFor(new Locator.WaitForOptions().setTimeout(3000));
			String text = qtyBadge.innerText().trim();
			return Integer.parseInt(text.replaceAll(ContinenteSelectors.NON_DIGIT_REGEX, ""));
		} catch (Exception _) {
			return 0;
		}
	}

	private void clearCartViaUi(Locator qtyBadge) {
		log.info("API call did not clear cart. Falling back to minicart popover...");
		openMinicartPopover();

		// Try clicking the Clear All button first
		if (tryClickClearAllButton(qtyBadge)) {
			return;
		}

		// Fallback: Remove items one by one
		removeItemsOneByOne();

		// Verify quantity after clearing
		String finalQtyText = "0";
		try {
			if (qtyBadge.isVisible()) {
				finalQtyText = qtyBadge.innerText().trim();
			}
		} catch (Exception _) {
			// Ignore read exceptions during validation
		}
		log.info("Cart clearing process completed. Final cart quantity: {}", finalQtyText);

		// Close popover to clean up the screen
		Locator closeBtn = page.locator(ContinenteSelectors.MINICART_CLOSE_BUTTON).first();
		if (closeBtn.isVisible()) {
			closeBtn.click();
			page.waitForTimeout(500);
		}
	}

	private void openMinicartPopover() {
		log.info("Opening minicart popover...");
		Locator minicart = page.locator(ContinenteSelectors.MINICART_SELECTOR).first();
		if (minicart.count() > 0) {
			try {
				minicart.scrollIntoViewIfNeeded();
				minicart.hover(new Locator.HoverOptions().setTimeout(3000));
				log.info("Hovered over minicart container.");
			} catch (Exception e) {
				log.warn("Failed to hover over minicart container: {}", e.getMessage());
			}
		}

		// Also hover over minicart-link as a backup
		try {
			Locator link = page.locator(".minicart-link:visible, .minicart-wrapper:visible").first();
			if (link.count() > 0) {
				link.hover(new Locator.HoverOptions().setTimeout(2000));
				log.info("Hovered over minicart link.");
			}
		} catch (Exception _) {
			// Ignore hover failures
		}

		// Dispatch hover events programmatically to trigger site JS reliably
		try {
			page.dispatchEvent(ContinenteSelectors.MINICART_SELECTOR, "mouseenter");
			page.dispatchEvent(ContinenteSelectors.MINICART_SELECTOR, "mouseover");
			page.dispatchEvent(ContinenteSelectors.MINICART_LINK_SELECTOR, "mouseenter");
			page.dispatchEvent(ContinenteSelectors.MINICART_LINK_SELECTOR, "mouseover");
		} catch (Exception e) {
			log.warn("Failed to dispatch hover events: {}", e.getMessage());
		}
		page.waitForTimeout(1500);

		// Wait up to 5 seconds for the minicart contents to load asynchronously (AJAX)
		try {
			page.locator(ContinenteSelectors.MINICART_CLEAN_OR_REMOVE).first()
					.waitFor(new Locator.WaitForOptions().setTimeout(5000));
			log.info("Minicart popover contents loaded successfully.");
		} catch (Exception e) {
			log.warn("Timed out waiting for minicart contents to load: {}", e.getMessage());
		}
	}

	private boolean tryClickClearAllButton(Locator qtyBadge) {
		Locator cleanAllBtn = page.locator(ContinenteSelectors.MINICART_CLEAN_ALL).first();
		if (!cleanAllBtn.isVisible()) {
			return false;
		}

		log.info("Found 'Clear All' button. Clicking it...");
		cleanAllBtn.click();
		page.waitForTimeout(800);

		// Handle confirmation modal if it appears
		Locator confirmBtn = page.locator(ContinenteSelectors.CONFIRM_CLEAN_MODAL_BUTTON).first();
		if (confirmBtn.isVisible()) {
			log.info("Found confirmation dialog. Clicking confirm button...");
			confirmBtn.click();
			page.waitForTimeout(1500);
		}

		// Wait to see if cart is cleared
		for (int i = 0; i < 10; i++) {
			page.waitForTimeout(200);
			try {
				if (!qtyBadge.isVisible()) {
					log.info("Cart cleared successfully using 'Clear All' button (badge is no longer visible).");
					return true;
				}
				String text = qtyBadge.innerText().trim();
				if (Integer.parseInt(text.replaceAll(ContinenteSelectors.NON_DIGIT_REGEX, "")) == 0) {
					log.info("Cart cleared successfully using 'Clear All' button.");
					return true;
				}
			} catch (Exception _) {
				log.info("Cart cleared successfully (exception reading badge, likely removed).");
				return true;
			}
		}
		log.warn("'Clear All' button did not clear the cart. Falling back to individual item removal...");
		return false;
	}

	private void removeItemsOneByOne() {
		int attempts = 0;
		while (attempts < 50) {
			Locator removeButtons = page.locator(ContinenteSelectors.MINICART_REMOVE_ITEM_BUTTON);
			int buttonsCount = removeButtons.count();
			if (buttonsCount == 0) {
				log.info("No more visible remove buttons in minicart popover.");
				break;
			}

			log.info("Removing item from cart (attempts={})...", attempts + 1);
			Locator firstBtn = removeButtons.first();
			firstBtn.scrollIntoViewIfNeeded();
			firstBtn.click();
			attempts++;

			// Wait for minicart quantities to update or AJAX call to finish.
			page.waitForTimeout(1000);
		}
	}

	/**
	 * Adds a product SKU to the shopping cart, increasing quantity if already
	 * present.
	 */
	public boolean addProductToCart(String externalId, int quantity) {
		log.info("Adding product SKU {} (quantity={}) to cart...", externalId, quantity);
		try {
			// Find the product tile by data-pid
			Locator tile = page.locator(String.format(ContinenteSelectors.PRODUCT_TILE_BY_PID, externalId, externalId))
					.first();
			if (!tile.isVisible()) {
				// Try searching for it first to get it on screen
				searchCallback.accept(externalId);
				tile = page.locator(String.format(ContinenteSelectors.PRODUCT_TILE_BY_PID, externalId, externalId))
						.first();
			}

			if (!tile.isVisible()) {
				log.error("Product tile with SKU {} not found on page.", externalId);
				return false;
			}

			// Scroll the tile into view
			tile.scrollIntoViewIfNeeded();

			// Define locators for quantity controls
			Locator plusBtn = tile.locator(ContinenteSelectors.INCREASE_QTY_BUTTON).first();
			Locator qtyInput = tile.locator(ContinenteSelectors.QTY_INPUT).first();
			Locator qtyDisplay = tile.locator(ContinenteSelectors.QTY_DISPLAY).first();

			int currentQty = getExistingQuantity(qtyInput, qtyDisplay, plusBtn, externalId);

			if (currentQty >= quantity) {
				log.info("Product SKU {} already has quantity {} (target is {}). No action needed.", externalId,
						currentQty, quantity);
				return true;
			}

			if (currentQty == 0) {
				// Locate add-to-cart button inside the tile
				Locator addBtn = tile.locator(ContinenteSelectors.ADD_TO_CART_BUTTON).first();
				if (!addBtn.isVisible() || addBtn.getAttribute("class").contains("disabled")) {
					log.error("Add to cart button not visible or is disabled for product SKU {}", externalId);
					return false;
				}

				// Click it to add the first item
				addBtn.click();
				page.waitForTimeout(1000);

				// Verify plus button is now visible to confirm it was added
				boolean updated = false;
				for (int attempt = 0; attempt < 15; attempt++) {
					if (plusBtn.isVisible()) {
						updated = true;
						break;
					}
					page.waitForTimeout(100);
				}
				if (!updated) {
					log.error("Failed to add SKU {} to cart (plus button did not appear).", externalId);
					return false;
				}
				currentQty = 1;
			}

			// If target quantity is greater than current quantity, adjust
			if (quantity > currentQty) {
				adjustProductQuantity(qtyInput, plusBtn, qtyDisplay, currentQty, quantity);
			}

			log.info("Successfully set SKU {} quantity to {} in cart.", externalId, quantity);
			return true;
		} catch (Exception e) {
			log.error("Failed to add SKU {} to cart: {}", externalId, e.getMessage());
			return false;
		}
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

	private void waitForNetworkIdle(int timeoutMs) {
		try {
			page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE,
					new Page.WaitForLoadStateOptions().setTimeout(timeoutMs));
		} catch (Exception e) {
			log.debug("Network idle wait timed out: {}", e.getMessage());
		}
	}
}
