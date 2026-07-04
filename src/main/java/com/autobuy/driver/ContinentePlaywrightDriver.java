package com.autobuy.driver;

import com.autobuy.model.SearchResult;
import com.microsoft.playwright.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Playwright browser automation driver for Continente Online.
 *
 * <p>
 * <b>SOLID Exception:</b> Deviates from SRP (Single Responsibility Principle)
 * by handling both site navigation and product HTML parsing. This is done to
 * avoid the overhead of spawning multiple browser contexts and to keep the
 * Playwright page session self-contained within this single driver.
 */
@Component
public class ContinentePlaywrightDriver implements SupermarketDriver {

	private static final Logger log = LoggerFactory.getLogger(ContinentePlaywrightDriver.class);

	private Playwright playwright;
	private Browser browser;
	private BrowserContext context;
	private Page page;

	private static final String BASE_URL = "https://www.continente.pt";
	private static final String NON_DIGIT_REGEX = "\\D";
	private static final String MINICART_SELECTOR = ".minicart:visible, .col-minicart:visible";
	private static final String MINICART_LINK_SELECTOR = ".minicart-link:visible";
	private static final String VALUE_ATTR = "value";
	private static final String ARIA_VALUENOW_ATTR = "aria-valuenow";
	private static final String PRODUCT_TILE_SELECTOR_FORMAT = "div[data-pid='%s'], .product-tile[data-pid='%s']";

	@Override
	public String getSupermarketName() {
		return "CONTINENTE";
	}

	@Override
	public void initialize(String username, String password, boolean headful) {
		log.info("Initializing Playwright browser context (headful={})...", headful);
		playwright = Playwright.create();

		BrowserType.LaunchOptions options = new BrowserType.LaunchOptions().setHeadless(!headful)
				.setArgs(List.of("--start-maximized")).setSlowMo(100);

		browser = playwright.chromium().launch(options);

		// Emulate a standard desktop user agent
		Browser.NewContextOptions contextOptions = new Browser.NewContextOptions().setUserAgent(
				"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
				.setViewportSize(null);

		context = browser.newContext(contextOptions);
		page = context.newPage();

		log.info("Navigating to Continente homepage...");
		page.navigate(BASE_URL);

		handleCookies();
		performLogin(username, password);
		if (username != null && !username.isBlank() && password != null && !password.isBlank()) {
			clearCart();
		}
	}

	private void handleCookies() {
		try {
			log.info("Checking for cookie consent banner...");
			if (!tryAcceptCookieById()) {
				tryAcceptCookieByText();
			}
		} catch (Exception e) {
			log.warn("Cookie handling skipped or encountered error: {}", e.getMessage());
		}
	}

	private boolean tryAcceptCookieById() {
		try {
			Locator cookieButton = page.locator("#CybotCookiebotDialogBodyLevelButtonLevelOptinAllowAll");
			cookieButton.waitFor(new Locator.WaitForOptions().setTimeout(1500));
			cookieButton.click();
			log.info("Accepted cookies via ID button.");
			return true;
		} catch (Exception _) {
			log.info("No Cybot cookie banner found within 1.5s, trying fallback...");
			return false;
		}
	}

	private boolean tryAcceptCookieByText() {
		try {
			Locator acceptTextButton = page.locator("button:has-text('Aceitar todos')");
			acceptTextButton.waitFor(new Locator.WaitForOptions().setTimeout(1000));
			acceptTextButton.click();
			log.info("Accepted cookies via text button ('Aceitar todos').");
			return true;
		} catch (Exception _) {
			log.info("No cookie consent banner detected.");
			return false;
		}
	}

	private void performLogin(String username, String password) {
		if (username == null || password == null || username.isBlank() || password.isBlank()) {
			log.warn("No login credentials provided. Skipping login step (guest mode).");
			return;
		}

		log.info("Attempting to log in as user: {}", username);
		try {
			// Click top-bar login button using robust visible desktop selectors
			clickLoginTrigger();

			log.info("Locating Continente SSO login iframe...");
			FrameLocator loginFrame = page.frameLocator("iframe[src*='login.continente.pt']");

			// Step 1: Enter Username (Email/Phone)
			log.info("Step 1: Entering username...");
			Locator emailField = loginFrame.locator("input#userNameCC, input[name='userNameCC']");
			emailField.waitFor(new Locator.WaitForOptions().setTimeout(15000));
			page.waitForTimeout(500); // Wait for page scripts to bind to form elements
			emailField.scrollIntoViewIfNeeded();
			emailField.click();
			emailField.clear();
			emailField.pressSequentially(username, new Locator.PressSequentiallyOptions().setDelay(100));

			Locator avancarBtn = loginFrame.locator("button:has-text('Avançar'), button[type='submit']").first();
			avancarBtn.scrollIntoViewIfNeeded();
			avancarBtn.click();

			// Step 2: Enter Password
			log.info("Step 2: Entering password...");
			Locator passwordField = loginFrame
					.locator("input#password_input, input[name='password_input'], input[type='password']");
			passwordField.waitFor(new Locator.WaitForOptions().setTimeout(15000));
			page.waitForTimeout(500);
			passwordField.scrollIntoViewIfNeeded();
			passwordField.click();
			passwordField.clear();
			passwordField.pressSequentially(password, new Locator.PressSequentiallyOptions().setDelay(100));

			Locator submitButton = loginFrame
					.locator("button:has-text('Avançar'), button:has-text('Entrar'), button[type='submit']").first();
			submitButton.scrollIntoViewIfNeeded();
			submitButton.click();

			// Wait for landing page or URL change (Home-Show, base URL, or logged in state)
			page.waitForURL(
					url -> url.contains("Home-Show") || url.equals("https://www.continente.pt/")
							|| (url.contains("continente.pt") && !url.contains("/login")),
					new Page.WaitForURLOptions().setTimeout(30000));
			log.info("Successfully logged in to Continente Online.");
		} catch (Exception e) {
			log.error("Login failed or timed out: {}", e.getMessage());
			try {
				java.io.File dir = new java.io.File("data");
				if (!dir.exists()) {
					dir.mkdirs();
				}
				page.screenshot(new Page.ScreenshotOptions().setPath(java.nio.file.Paths.get("data/login-failed.png")));
				log.info("Saved failure screenshot to data/login-failed.png");
			} catch (Exception ex) {
				log.error("Failed to take failure screenshot: {}", ex.getMessage());
			}
			throw new RuntimeException(
					"Authentication failed: Continente Online login page did not redirect. CAPTCHA, block, or invalid credentials may have occurred.",
					e);
		}
	}

	private void clickLoginTrigger() {
		Locator loginTrigger = page.locator(
				"#headerLogin:visible, a.user-login:visible, a:has-text('Login/Registo'):visible, a:has-text('Login'):visible, a:has-text('Entrar'):visible")
				.first();
		try {
			loginTrigger.waitFor(new Locator.WaitForOptions().setTimeout(7000));
			log.info("Login trigger button found. Clicking it...");
			loginTrigger.click();
		} catch (Exception _) {
			// If button is hidden or times out, navigate directly to the login flow URL
			log.info("Login trigger button not visible or found. Navigating directly to login page...");
			page.navigate(BASE_URL + "/login/");
		}
	}

	private void clearCart() {
		log.info("Checking if cart needs to be cleared...");
		try {
			Locator qtyBadge = page.locator(".minicart-quantity").first();
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
			return Integer.parseInt(qtyText.replaceAll(NON_DIGIT_REGEX, ""));
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
			page.navigate(BASE_URL);
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
				return Integer.parseInt(text.replaceAll(NON_DIGIT_REGEX, ""));
			}
			// Wait up to 3 seconds for the badge to become visible if it has items
			qtyBadge.waitFor(new Locator.WaitForOptions().setTimeout(3000));
			String text = qtyBadge.innerText().trim();
			return Integer.parseInt(text.replaceAll(NON_DIGIT_REGEX, ""));
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
		Locator closeBtn = page.locator("button.minicart-close").first();
		if (closeBtn.isVisible()) {
			closeBtn.click();
			page.waitForTimeout(500);
		}
	}

	private void openMinicartPopover() {
		log.info("Opening minicart popover...");
		Locator minicart = page.locator(MINICART_SELECTOR).first();
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
			page.dispatchEvent(MINICART_SELECTOR, "mouseenter");
			page.dispatchEvent(MINICART_SELECTOR, "mouseover");
			page.dispatchEvent(MINICART_LINK_SELECTOR, "mouseenter");
			page.dispatchEvent(MINICART_LINK_SELECTOR, "mouseover");
		} catch (Exception e) {
			log.warn("Failed to dispatch hover events: {}", e.getMessage());
		}
		page.waitForTimeout(1500);

		// Wait up to 5 seconds for the minicart contents to load asynchronously (AJAX)
		try {
			page.locator("button.minicart-clean-button, button.minicart-remove-product").first()
					.waitFor(new Locator.WaitForOptions().setTimeout(5000));
			log.info("Minicart popover contents loaded successfully.");
		} catch (Exception e) {
			log.warn("Timed out waiting for minicart contents to load: {}", e.getMessage());
		}
	}

	private boolean tryClickClearAllButton(Locator qtyBadge) {
		Locator cleanAllBtn = page.locator("button.minicart-clean-button, button[aria-label*='Limpar todos']").first();
		if (!cleanAllBtn.isVisible()) {
			return false;
		}

		log.info("Found 'Clear All' button. Clicking it...");
		cleanAllBtn.click();
		page.waitForTimeout(800);

		// Handle confirmation modal if it appears
		Locator confirmBtn = page.locator(
				".modal-dialog button:has-text('Confirmar'), .modal button:has-text('Limpar'), button.confirm-confirm-clean-cart-select, button:has-text('Confirmar'), button:has-text('Sim'), button.confirm-btn")
				.first();
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
				if (Integer.parseInt(text.replaceAll(NON_DIGIT_REGEX, "")) == 0) {
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
			Locator removeButtons = page.locator(".minicart-popover button.minicart-remove-product:visible");
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

	@Override
	public List<SearchResult> searchProduct(String query) {
		log.info("Searching supermarket for query: '{}'...", query);
		List<SearchResult> results = new ArrayList<>();

		try {
			// Locate search bar
			Locator searchInput = page.locator("input[name='q'], input[placeholder*='procura'], input[type='search']")
					.first();
			searchInput.waitFor(new Locator.WaitForOptions().setTimeout(5000));
			searchInput.fill(query);
			searchInput.press("Enter");

			// Wait for results grid or message
			page.waitForLoadState();

			// Wait for product tiles to appear
			Locator tiles = page.locator("div.product[data-pid], div.product-tile[data-pid]");
			if (!waitForProductTiles(tiles, query)) {
				return results;
			}

			int count = Math.min(tiles.count(), 5); // Extract top 5 results
			log.info("Found {} raw candidates. Parsing top {}...", tiles.count(), count);

			for (int i = 0; i < count; i++) {
				Locator tile = tiles.nth(i);
				parseSingleProductTile(tile, results);
			}
		} catch (Exception e) {
			log.error("Error occurred while searching for '{}': {}", query, e.getMessage(), e);
		}

		return results;
	}

	private void parseSingleProductTile(Locator tile, List<SearchResult> results) {
		try {
			String externalId = extractExternalId(tile);
			Locator titleLink = tile
					.locator(".ct-pdp-link a, a.pdp-link, .ct-tile--title a, .ct-pdp-link, .ct-tile--title").first();
			String name = extractProductName(titleLink);
			String url = extractProductUrl(titleLink);
			String brand = extractProductBrand(tile, name);
			BigDecimal priceValue = extractProductPrice(tile, name);
			String category = "Supermercado";

			if (externalId != null && !externalId.isBlank() && !name.isBlank()) {
				results.add(new SearchResult(externalId, name, brand, priceValue, url, category));
			}
		} catch (Exception e) {
			log.warn("Failed to parse single product tile: {}", e.getMessage());
		}
	}

	private String extractExternalId(Locator tile) {
		String externalId = tile.getAttribute("data-pid");
		if (externalId == null || externalId.isBlank()) {
			String idAttr = tile.getAttribute("id");
			if (idAttr != null) {
				externalId = idAttr.replaceAll(NON_DIGIT_REGEX, "");
			}
		}
		return externalId;
	}

	private String extractProductName(Locator titleLink) {
		if (titleLink.count() > 0) {
			return titleLink.innerText().trim();
		}
		return "";
	}

	private String extractProductUrl(Locator titleLink) {
		String url = BASE_URL;
		if (titleLink.count() > 0 && titleLink.getAttribute("href") != null) {
			url = titleLink.getAttribute("href");
			if (!url.startsWith("http")) {
				url = BASE_URL + url;
			}
		}
		return url;
	}

	private String extractProductBrand(Locator tile, String name) {
		Locator brandLoc = tile.locator(".product-brand, .ct-tile--brand").first();
		if (brandLoc.count() > 0 && brandLoc.isVisible()) {
			return brandLoc.innerText().trim();
		}
		String[] words = name.split(" ");
		if (words.length > 0) {
			return words[0];
		}
		return "Generico";
	}

	private BigDecimal extractProductPrice(Locator tile, String name) {
		Locator primaryLoc = tile.locator(".pwc-tile--price-primary, .ct-price-value, .ct-price-formatted").first();
		if (primaryLoc.count() == 0) {
			primaryLoc = tile.locator(".value, .price").first();
		}
		Locator secondaryLoc = tile.locator(".pwc-tile--price-secondary, .ct-price-unit, .price-per-unit").first();

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

	@Override
	public boolean isProductAvailable(String externalId) {
		log.info("Checking availability for SKU {}...", externalId);
		try {
			// Find the product tile by data-pid
			Locator tile = page.locator(String.format(PRODUCT_TILE_SELECTOR_FORMAT, externalId, externalId)).first();
			if (!tile.isVisible()) {
				// Try searching for it first to get it on screen
				searchProduct(externalId);
				tile = page.locator(String.format(PRODUCT_TILE_SELECTOR_FORMAT, externalId, externalId)).first();
			}

			if (!tile.isVisible()) {
				log.warn("Product SKU {} not found on page during availability check.", externalId);
				return false;
			}

			// Scroll the tile into view
			tile.scrollIntoViewIfNeeded();

			// Define selectors for quantity controls based on DOM inspection
			Locator plusBtn = tile.locator("button.increase-quantity-btn").first();
			Locator qtyInput = tile.locator("input.add-to-cart-quantity, input.quantity-select, .quantity-value-mask")
					.first();

			// If it's already in the cart (plus button or qty input is visible), it's
			// available
			if (plusBtn.isVisible() || qtyInput.isVisible()) {
				return true;
			}

			// Otherwise, check if the add-to-cart button is visible and enabled
			Locator addBtn = tile
					.locator("button.add-to-cart, button:has-text('Adicionar'), .ct-tile--add-to-cart button").first();
			return addBtn.isVisible() && addBtn.isEnabled();
		} catch (Exception e) {
			log.error("Error checking availability for SKU {}: {}", externalId, e.getMessage());
			return false;
		}
	}

	@Override
	public boolean addProductToCart(String externalId, int quantity) {
		log.info("Adding product SKU {} (quantity={}) to cart...", externalId, quantity);
		try {
			// Find the product tile by data-pid
			Locator tile = page.locator(String.format(PRODUCT_TILE_SELECTOR_FORMAT, externalId, externalId)).first();
			if (!tile.isVisible()) {
				// Try searching for it first to get it on screen
				searchProduct(externalId);
				tile = page.locator(String.format(PRODUCT_TILE_SELECTOR_FORMAT, externalId, externalId)).first();
			}

			if (!tile.isVisible()) {
				log.error("Product tile with SKU {} not found on page.", externalId);
				return false;
			}

			// Scroll the tile into view
			tile.scrollIntoViewIfNeeded();

			// Define selectors for quantity controls based on DOM inspection
			Locator plusBtn = tile.locator("button.increase-quantity-btn").first();
			Locator qtyInput = tile.locator("input.add-to-cart-quantity, input.quantity-select, .quantity-value-mask")
					.first();
			Locator qtyDisplay = tile.locator(".quantity-value, .qty, .ct-tile-quantity").first();

			int currentQty = getExistingQuantity(qtyInput, qtyDisplay, plusBtn, externalId);

			if (currentQty >= quantity) {
				log.info("Product SKU {} already has quantity {} (target is {}). No action needed.", externalId,
						currentQty, quantity);
				return true;
			}

			if (currentQty == 0) {
				// Locate add-to-cart button inside the tile
				Locator addBtn = tile
						.locator("button.add-to-cart, button:has-text('Adicionar'), .ct-tile--add-to-cart button")
						.first();
				if (!addBtn.isVisible()) {
					log.error("Add to cart button not visible for product SKU {}", externalId);
					return false;
				}

				// Click it to add the first item
				addBtn.click();
				page.waitForTimeout(1000);
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
		// A plus button or visible quantity input means it's already in the cart
		if (qtyInput.isVisible() || plusBtn.isVisible()) {
			String qtyText = readRawQuantityText(qtyInput, qtyDisplay);
			try {
				currentQty = Integer.parseInt(qtyText.replaceAll(NON_DIGIT_REGEX, ""));
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
			qtyText = qtyInput.getAttribute(VALUE_ATTR);
			if (qtyText == null || qtyText.isEmpty()) {
				qtyText = qtyInput.getAttribute(ARIA_VALUENOW_ATTR);
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
			String val = qtyInput.getAttribute(VALUE_ATTR);
			if (val == null || val.isEmpty()) {
				val = qtyInput.getAttribute(ARIA_VALUENOW_ATTR);
			}
			try {
				if (Integer.parseInt(val.replaceAll(NON_DIGIT_REGEX, "")) == quantity) {
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
				int val = Integer.parseInt(currentText.replaceAll(NON_DIGIT_REGEX, ""));
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

	private boolean waitForProductTiles(Locator tiles, String query) {
		try {
			tiles.first().waitFor(new Locator.WaitForOptions().setTimeout(8000));
			return true;
		} catch (Exception _) {
			log.warn("No product tiles loaded for search '{}' within timeout.", query);
			return false;
		}
	}

	public Page getPage() {
		return page;
	}

	@Override
	public void close() {
		log.info("Closing Playwright browser context...");
		try {
			if (context != null)
				context.close();
			if (browser != null)
				browser.close();
			if (playwright != null)
				playwright.close();
			log.info("Browser closed successfully.");
		} catch (Exception e) {
			log.error("Error closing Playwright browser: {}", e.getMessage());
		}
	}

	/**
	 * Parses pricing strings (e.g. "1,49 €", "€ 10.99") into a BigDecimal.
	 */
	private BigDecimal parsePrice(String priceText) {
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
