package com.autobuy.driver.continente;

import com.autobuy.driver.SupermarketDriver;
import com.autobuy.model.SearchResult;
import com.microsoft.playwright.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Playwright browser automation driver for Continente Online. Delegates parsing
 * to {@link ContinenteParser} and cart actions to
 * {@link ContinenteCartManager}.
 */
@Component
public class ContinentePlaywrightDriver implements SupermarketDriver {

	private static final Logger log = LoggerFactory.getLogger(ContinentePlaywrightDriver.class);

	private Playwright playwright;
	private Browser browser;
	private BrowserContext context;
	private Page page;
	private ContinenteCartManager cartManager;

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

		// Use Playwright's default User-Agent, which always matches the bundled
		// Chromium version
		Browser.NewContextOptions contextOptions = new Browser.NewContextOptions().setViewportSize(null);

		context = browser.newContext(contextOptions);
		page = context.newPage();

		// Create cart manager with search callback to dynamically handle out-of-screen
		// products
		cartManager = new ContinenteCartManager(page, this::searchProduct);

		log.info("Navigating to Continente homepage...");
		page.navigate(ContinenteSelectors.BASE_URL);

		handleCookies();
		performLogin(username, password);
		if (username != null && !username.isBlank() && password != null && !password.isBlank()) {
			cartManager.clearCart();
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
			Locator cookieButton = page.locator(ContinenteSelectors.COOKIE_BY_ID_BUTTON);
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
			Locator acceptTextButton = page.locator(ContinenteSelectors.COOKIE_BY_TEXT_BUTTON);
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
			clickLoginTrigger();

			log.info("Locating Continente SSO login iframe...");
			FrameLocator loginFrame = page.frameLocator(ContinenteSelectors.LOGIN_IFRAME);

			// Step 1: Enter Username (Email/Phone)
			log.info("Step 1: Entering username...");
			Locator emailField = loginFrame.locator(ContinenteSelectors.USERNAME_INPUT);
			emailField.waitFor(new Locator.WaitForOptions().setTimeout(15000));
			page.waitForTimeout(500); // Wait for page scripts to bind to form elements
			emailField.scrollIntoViewIfNeeded();
			emailField.click();
			emailField.clear();
			emailField.pressSequentially(username, new Locator.PressSequentiallyOptions().setDelay(100));

			Locator avancarBtn = loginFrame.locator(ContinenteSelectors.AVANCAR_BUTTON).first();
			avancarBtn.scrollIntoViewIfNeeded();
			avancarBtn.click();

			// Step 2: Enter Password
			log.info("Step 2: Entering password...");
			Locator passwordField = loginFrame.locator(ContinenteSelectors.PASSWORD_INPUT);
			passwordField.waitFor(new Locator.WaitForOptions().setTimeout(15000));
			page.waitForTimeout(500);
			passwordField.scrollIntoViewIfNeeded();
			passwordField.click();
			passwordField.clear();
			passwordField.pressSequentially(password, new Locator.PressSequentiallyOptions().setDelay(100));

			Locator submitButton = loginFrame.locator(ContinenteSelectors.SUBMIT_BUTTON).first();
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
		Locator loginTrigger = page.locator(ContinenteSelectors.LOGIN_TRIGGER).first();
		try {
			loginTrigger.waitFor(new Locator.WaitForOptions().setTimeout(7000));
			log.info("Login trigger button found. Clicking it...");
			loginTrigger.click();
		} catch (Exception _) {
			log.info("Login trigger button not visible or found. Navigating directly to login page...");
			page.navigate(ContinenteSelectors.BASE_URL + "/login/");
		}
	}

	@Override
	public List<SearchResult> searchProduct(String query) {
		log.info("Searching supermarket for query: '{}'...", query);
		List<SearchResult> results = new ArrayList<>();

		try {
			// Locate search bar
			Locator searchInput = page.locator(ContinenteSelectors.SEARCH_INPUT).first();
			searchInput.waitFor(new Locator.WaitForOptions().setTimeout(5000));
			searchInput.fill(query);
			searchInput.press("Enter");

			// Wait for results grid or message
			page.waitForLoadState();

			// Wait for product tiles to appear
			Locator tiles = page.locator(ContinenteSelectors.PRODUCT_TILES);
			if (!waitForProductTiles(tiles, query)) {
				return results;
			}

			int count = Math.min(tiles.count(), 6); // Extract top 6 results
			log.info("Found {} raw candidates. Parsing top {}...", tiles.count(), count);

			for (int i = 0; i < count; i++) {
				Locator tile = tiles.nth(i);
				ContinenteParser.parseSingleProductTile(tile, results);
			}
		} catch (Exception e) {
			log.error("Error occurred while searching for '{}': {}", query, e.getMessage(), e);
		}

		return results;
	}

	@Override
	public boolean isProductAvailable(String externalId) {
		log.info("Checking availability for SKU {}...", externalId);
		try {
			// Find the product tile by data-pid
			Locator tile = page.locator(String.format(ContinenteSelectors.PRODUCT_TILE_BY_PID, externalId, externalId))
					.first();
			if (!tile.isVisible()) {
				// Try searching for it first to get it on screen
				searchProduct(externalId);
				tile = page.locator(String.format(ContinenteSelectors.PRODUCT_TILE_BY_PID, externalId, externalId))
						.first();
			}

			if (!tile.isVisible()) {
				log.warn("Product SKU {} not found on page during availability check.", externalId);
				return false;
			}

			// Scroll the tile into view
			tile.scrollIntoViewIfNeeded();

			// Define selectors for quantity controls based on DOM inspection
			Locator plusBtn = tile.locator(ContinenteSelectors.INCREASE_QTY_BUTTON).first();
			Locator qtyInput = tile.locator(ContinenteSelectors.QTY_INPUT).first();

			// If it's already in the cart (plus button or qty input is visible), it's
			// available
			if (plusBtn.isVisible() || qtyInput.isVisible()) {
				return true;
			}

			// Otherwise, check if the add-to-cart button is visible and enabled
			Locator addBtn = tile.locator(ContinenteSelectors.ADD_TO_CART_BUTTON).first();
			return addBtn.isVisible() && addBtn.isEnabled();
		} catch (Exception e) {
			log.error("Error checking availability for SKU {}: {}", externalId, e.getMessage());
			return false;
		}
	}

	@Override
	public boolean addProductToCart(String externalId, int quantity) {
		if (cartManager == null) {
			log.error("CartManager is not initialized. Initialize the driver first.");
			return false;
		}
		return cartManager.addProductToCart(externalId, quantity);
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
	public void navigateToCart() {
		log.info("Navigating to Continente cart page: {}", ContinenteSelectors.CART_URL);
		try {
			page.navigate(ContinenteSelectors.CART_URL);
			page.waitForLoadState();
		} catch (Exception e) {
			log.error("Failed to navigate to cart page: {}", e.getMessage(), e);
		}
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
}
