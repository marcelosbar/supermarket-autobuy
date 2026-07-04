package com.autobuy.driver;

/**
 * Constants class containing selectors and attributes for the Continente Online
 * website.
 */
public final class ContinenteSelectors {

	private ContinenteSelectors() {
		// Prevent instantiation
	}

	public static final String BASE_URL = "https://www.continente.pt";
	public static final String NON_DIGIT_REGEX = "\\D";
	public static final String VALUE_ATTR = "value";
	public static final String ARIA_VALUENOW_ATTR = "aria-valuenow";

	// Cookies selectors
	public static final String COOKIE_BY_ID_BUTTON = "#CybotCookiebotDialogBodyLevelButtonLevelOptinAllowAll";
	public static final String COOKIE_BY_TEXT_BUTTON = "button:has-text('Aceitar todos')";

	// Login selectors
	public static final String LOGIN_TRIGGER = "#headerLogin:visible, a.user-login:visible, a:has-text('Login/Registo'):visible, a:has-text('Login'):visible, a:has-text('Entrar'):visible";
	public static final String LOGIN_IFRAME = "iframe[src*='login.continente.pt']";
	public static final String USERNAME_INPUT = "input#userNameCC, input[name='userNameCC']";
	public static final String AVANCAR_BUTTON = "button:has-text('Avançar'), button[type='submit']";
	public static final String PASSWORD_INPUT = "input#password_input, input[name='password_input'], input[type='password']";
	public static final String SUBMIT_BUTTON = "button:has-text('Avançar'), button:has-text('Entrar'), button[type='submit']";

	// Cart selectors
	public static final String MINICART_SELECTOR = ".minicart:visible, .col-minicart:visible";
	public static final String MINICART_LINK_SELECTOR = ".minicart-link:visible";
	public static final String MINICART_QUANTITY = ".minicart-quantity";
	public static final String MINICART_CLEAN_OR_REMOVE = "button.minicart-clean-button, button.minicart-remove-product";
	public static final String MINICART_CLEAN_ALL = "button.minicart-clean-button, button[aria-label*='Limpar todos']";
	public static final String CONFIRM_CLEAN_MODAL_BUTTON = ".modal-dialog button:has-text('Confirmar'), .modal button:has-text('Limpar'), button.confirm-confirm-clean-cart-select, button:has-text('Confirmar'), button:has-text('Sim'), button.confirm-btn";
	public static final String MINICART_REMOVE_ITEM_BUTTON = ".minicart-popover button.minicart-remove-product:visible";
	public static final String MINICART_CLOSE_BUTTON = "button.minicart-close";

	// Search & Product selectors
	public static final String SEARCH_INPUT = "input[name='q'], input[placeholder*='procura'], input[type='search']";
	public static final String PRODUCT_TILES = "div.product[data-pid], div.product-tile[data-pid]";
	public static final String PRODUCT_TITLE_LINK = ".ct-pdp-link a, a.pdp-link, .ct-tile--title a, .ct-pdp-link, .ct-tile--title";
	public static final String PRODUCT_BRAND = ".product-brand, .ct-tile--brand";
	public static final String PRICE_PRIMARY = ".pwc-tile--price-primary, .ct-price-value, .ct-price-formatted";
	public static final String PRICE_FALLBACK = ".value, .price";
	public static final String PRICE_SECONDARY = ".pwc-tile--price-secondary, .ct-price-unit, .price-per-unit";

	// Add to Cart / Quantity selectors
	public static final String PRODUCT_TILE_BY_PID = "div[data-pid='%s'], .product-tile[data-pid='%s']";
	public static final String INCREASE_QTY_BUTTON = "button.increase-quantity-btn";
	public static final String QTY_INPUT = "input.add-to-cart-quantity, input.quantity-select, .quantity-value-mask";
	public static final String QTY_DISPLAY = ".quantity-value, .qty, .ct-tile-quantity";
	public static final String ADD_TO_CART_BUTTON = "button.add-to-cart, button:has-text('Adicionar'), .ct-tile--add-to-cart button";
}
