package com.autobuy.driver.continente;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContinenteCartManagerTest {

	private static final String DEFAULT_SKU = "12345";
	private static final String ADD_TO_CART_CLASS = "add-to-cart";
	private static final String CLASS_ATTR = "class";

	@Mock
	private Page page;

	@Mock
	private Consumer<String> searchCallback;

	@Mock
	private Locator badgeLocator;

	@Mock
	private Locator badgeItemLocator;

	@Mock
	private Locator tileLocator;

	@Mock
	private Locator tileFirstLocator;

	@Mock
	private Locator addBtnLocator;

	@Mock
	private Locator plusBtnLocator;

	@Mock
	private Locator qtyInputLocator;

	@Mock
	private Locator qtyDisplayLocator;

	@Mock
	private Locator cleanBtnLocator;

	@Mock
	private Locator confirmBtnLocator;

	private ContinenteCartManager cartManager;

	@BeforeEach
	void setUp() {
		cartManager = new ContinenteCartManager(page, searchCallback);
	}

	@Test
	void clearCart_cartAlreadyEmpty_doesNotTriggerClearActions() {
		// Arrange
		when(page.locator(ContinenteSelectors.MINICART_QUANTITY)).thenReturn(badgeLocator);
		when(badgeLocator.count()).thenReturn(1);
		when(badgeLocator.nth(0)).thenReturn(badgeItemLocator);
		when(badgeItemLocator.textContent()).thenReturn("0");

		// Act
		cartManager.clearCart();

		// Assert
		verify(page, never()).evaluate(anyString());
		verify(page, never()).navigate(ContinenteSelectors.CART_URL);
	}

	@Test
	void clearCart_itemsInCart_clearsCartViaApiSuccessfully() {
		// Arrange
		when(page.locator(ContinenteSelectors.MINICART_QUANTITY)).thenReturn(badgeLocator);
		when(badgeLocator.count()).thenReturn(1);
		when(badgeLocator.nth(0)).thenReturn(badgeItemLocator);
		when(badgeItemLocator.textContent()).thenReturn("3", "0");

		// Act
		cartManager.clearCart();

		// Assert
		verify(page).evaluate(anyString());
		verify(page).navigate(ContinenteSelectors.BASE_URL);
		verify(page, never()).navigate(ContinenteSelectors.CART_URL);
	}

	@Test
	void clearCart_apiFails_fallsBackToCartPageClearing() {
		// Arrange
		when(page.locator(ContinenteSelectors.MINICART_QUANTITY)).thenReturn(badgeLocator);
		when(badgeLocator.count()).thenReturn(1);
		when(badgeLocator.nth(0)).thenReturn(badgeItemLocator);
		when(badgeItemLocator.textContent()).thenReturn("2", "2", "0");

		when(page.locator(contains("minicart-clean-button"))).thenReturn(cleanBtnLocator);
		when(cleanBtnLocator.first()).thenReturn(cleanBtnLocator);
		when(cleanBtnLocator.isVisible()).thenReturn(true);

		when(page.locator(ContinenteSelectors.CONFIRM_CLEAN_MODAL_BUTTON)).thenReturn(confirmBtnLocator);
		when(confirmBtnLocator.first()).thenReturn(confirmBtnLocator);
		when(confirmBtnLocator.isVisible()).thenReturn(true);

		// Act
		cartManager.clearCart();

		// Assert
		verify(page).navigate(ContinenteSelectors.CART_URL);
		verify(cleanBtnLocator).click();
		verify(confirmBtnLocator).click();
		verify(page, atLeastOnce()).navigate(ContinenteSelectors.BASE_URL);
	}

	@Test
	void clearCart_exceptionThrown_handlesExceptionGracefully() {
		// Arrange
		when(page.locator(ContinenteSelectors.MINICART_QUANTITY)).thenThrow(new RuntimeException("Page error"));

		// Act
		cartManager.clearCart();

		// Assert
		verify(page, never()).evaluate(anyString());
	}

	@Test
	void addProductToCart_tileNotFoundEvenAfterSearch_returnsFalse() {
		// Arrange
		setupEmptyMinicart();
		String selector = String.format(ContinenteSelectors.PRODUCT_TILE_BY_PID, DEFAULT_SKU, DEFAULT_SKU);

		when(page.locator(selector)).thenReturn(tileLocator);
		when(tileLocator.first()).thenReturn(tileFirstLocator);
		when(tileFirstLocator.isVisible()).thenReturn(false);

		// Act
		boolean result = cartManager.addProductToCart(DEFAULT_SKU, 1);

		// Assert
		assertFalse(result);
		verify(searchCallback).accept(DEFAULT_SKU);
	}

	@Test
	void addProductToCart_alreadyHasTargetQuantity_returnsTrue() {
		// Arrange
		setupEmptyMinicart();
		setupProductTileLocators(DEFAULT_SKU);

		when(tileFirstLocator.isVisible()).thenReturn(true);
		when(plusBtnLocator.isVisible()).thenReturn(true);
		when(qtyInputLocator.count()).thenReturn(1);
		when(qtyInputLocator.getAttribute(ContinenteSelectors.VALUE_ATTR)).thenReturn("2");

		// Act
		boolean result = cartManager.addProductToCart(DEFAULT_SKU, 2);

		// Assert
		assertTrue(result);
		verify(tileFirstLocator).scrollIntoViewIfNeeded();
		verify(addBtnLocator, never()).click();
	}

	@Test
	void addProductToCart_addButtonDisabled_returnsFalse() {
		// Arrange
		setupEmptyMinicart();
		setupProductTileLocators(DEFAULT_SKU);

		when(tileFirstLocator.isVisible()).thenReturn(true);
		when(plusBtnLocator.isVisible()).thenReturn(false);
		when(addBtnLocator.isVisible()).thenReturn(true);
		when(addBtnLocator.getAttribute(CLASS_ATTR)).thenReturn("add-to-cart disabled");

		// Act
		boolean result = cartManager.addProductToCart(DEFAULT_SKU, 1);

		// Assert
		assertFalse(result);
		verify(addBtnLocator, never()).click();
	}

	@Test
	void addProductToCart_outOfStockProduct_returnsFalse() {
		// Arrange
		setupEmptyMinicart();
		setupProductTileLocators(DEFAULT_SKU);

		when(tileFirstLocator.isVisible()).thenReturn(true);
		when(plusBtnLocator.isVisible()).thenReturn(false);
		when(addBtnLocator.isVisible()).thenReturn(true);
		when(addBtnLocator.getAttribute(CLASS_ATTR)).thenReturn(ADD_TO_CART_CLASS);
		when(tileFirstLocator.getAttribute(CLASS_ATTR)).thenReturn("ct-product-tile-out-of-stock");

		// Act
		boolean result = cartManager.addProductToCart(DEFAULT_SKU, 1);

		// Assert
		assertFalse(result);
		verify(addBtnLocator).click();
	}

	@Test
	void addProductToCart_newProductDirectQuantityInput_addsAndAdjustsSuccessfully() {
		// Arrange
		setupEmptyMinicart();
		setupProductTileLocators(DEFAULT_SKU);

		when(tileFirstLocator.isVisible()).thenReturn(true);
		when(plusBtnLocator.isVisible()).thenReturn(false, true);
		when(addBtnLocator.isVisible()).thenReturn(true);
		when(addBtnLocator.getAttribute(CLASS_ATTR)).thenReturn(ADD_TO_CART_CLASS);

		when(qtyInputLocator.isVisible()).thenReturn(true);
		when(qtyInputLocator.isEnabled()).thenReturn(true);
		when(qtyInputLocator.getAttribute(ContinenteSelectors.VALUE_ATTR)).thenReturn("3");

		// Act
		boolean result = cartManager.addProductToCart(DEFAULT_SKU, 3);

		// Assert
		assertTrue(result);
		verify(addBtnLocator).click();
		verify(qtyInputLocator).fill("3");
		verify(qtyInputLocator).press("Enter");
	}

	@Test
	void addProductToCart_directInputFails_fallsBackToClickIncrementLoop() {
		// Arrange
		setupEmptyMinicart();
		setupProductTileLocators(DEFAULT_SKU);

		when(tileFirstLocator.isVisible()).thenReturn(true);
		when(plusBtnLocator.isVisible()).thenReturn(false, true);
		when(addBtnLocator.isVisible()).thenReturn(true);
		when(addBtnLocator.getAttribute(CLASS_ATTR)).thenReturn(ADD_TO_CART_CLASS);

		when(qtyInputLocator.isVisible()).thenReturn(false);
		when(qtyInputLocator.count()).thenReturn(1);
		when(qtyInputLocator.getAttribute(ContinenteSelectors.VALUE_ATTR)).thenReturn("2");

		// Act
		boolean result = cartManager.addProductToCart(DEFAULT_SKU, 2);

		// Assert
		assertTrue(result);
		verify(addBtnLocator).click();
		verify(plusBtnLocator).click();
	}

	@Test
	void addProductToCart_exceptionThrown_returnsFalse() {
		// Arrange
		when(page.locator(ContinenteSelectors.MINICART_QUANTITY)).thenThrow(new RuntimeException("Unexpected error"));

		// Act
		boolean result = cartManager.addProductToCart(DEFAULT_SKU, 1);

		// Assert
		assertFalse(result);
	}

	private void setupEmptyMinicart() {
		when(page.locator(ContinenteSelectors.MINICART_QUANTITY)).thenReturn(badgeLocator);
		when(badgeLocator.count()).thenReturn(1);
		when(badgeLocator.nth(0)).thenReturn(badgeItemLocator);
		when(badgeItemLocator.textContent()).thenReturn("0");
	}

	private void setupProductTileLocators(String externalId) {
		String selector = String.format(ContinenteSelectors.PRODUCT_TILE_BY_PID, externalId, externalId);
		when(page.locator(selector)).thenReturn(tileLocator);
		when(tileLocator.first()).thenReturn(tileFirstLocator);

		when(tileFirstLocator.locator(ContinenteSelectors.INCREASE_QTY_BUTTON)).thenReturn(plusBtnLocator);
		when(plusBtnLocator.first()).thenReturn(plusBtnLocator);

		when(tileFirstLocator.locator(ContinenteSelectors.QTY_INPUT)).thenReturn(qtyInputLocator);
		when(qtyInputLocator.first()).thenReturn(qtyInputLocator);

		lenient().when(tileFirstLocator.locator(ContinenteSelectors.QTY_DISPLAY)).thenReturn(qtyDisplayLocator);
		lenient().when(qtyDisplayLocator.first()).thenReturn(qtyDisplayLocator);

		lenient().when(tileFirstLocator.locator(ContinenteSelectors.ADD_TO_CART_BUTTON)).thenReturn(addBtnLocator);
		lenient().when(addBtnLocator.first()).thenReturn(addBtnLocator);
	}
}
