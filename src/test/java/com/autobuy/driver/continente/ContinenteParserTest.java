package com.autobuy.driver.continente;

import com.microsoft.playwright.Locator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ContinenteParserTest {

	@Test
	void parsePrice_standardEuropeanFormat_returnsBigDecimal() {
		// Arrange & Act & Assert
		assertEquals(new BigDecimal("1.49"), ContinenteParser.parsePrice("1,49 €"));
		assertEquals(new BigDecimal("1.49"), ContinenteParser.parsePrice("1,49€"));
		assertEquals(new BigDecimal("1.49"), ContinenteParser.parsePrice("1.49 €"));
	}

	@Test
	void parsePrice_standardUSFormat_returnsBigDecimal() {
		// Arrange & Act & Assert
		assertEquals(new BigDecimal("10.99"), ContinenteParser.parsePrice("€ 10.99"));
		assertEquals(new BigDecimal("10.99"), ContinenteParser.parsePrice("10.99"));
	}

	@Test
	void parsePrice_largeNumberWithThousandsSeparator_returnsBigDecimal() {
		// Arrange & Act & Assert
		assertEquals(new BigDecimal("1250.45"), ContinenteParser.parsePrice("1.250,45 €"));
	}

	@Test
	void parsePrice_leadingDecimalDotOrComma_returnsBigDecimal() {
		// Arrange & Act & Assert
		assertEquals(new BigDecimal("0.89"), ContinenteParser.parsePrice(".89 €"));
		assertEquals(new BigDecimal("0.89"), ContinenteParser.parsePrice(",89 €"));
	}

	@Test
	void parsePrice_emptyOrNullString_returnsZero() {
		// Arrange & Act & Assert
		assertEquals(BigDecimal.ZERO, ContinenteParser.parsePrice(""));
		assertEquals(BigDecimal.ZERO, ContinenteParser.parsePrice("   "));
		assertEquals(BigDecimal.ZERO, ContinenteParser.parsePrice(null));
	}

	@Test
	void parsePrice_invalidText_returnsZero() {
		// Arrange & Act & Assert
		assertEquals(BigDecimal.ZERO, ContinenteParser.parsePrice("price unknown"));
		assertEquals(BigDecimal.ZERO, ContinenteParser.parsePrice("abc"));
	}

	@Test
	void extractProductQuantity_validQuantityElement_returnsQuantityString() {
		// Arrange
		Locator tile = mock(Locator.class);
		Locator qtyLoc = mock(Locator.class);
		Locator firstQtyLoc = mock(Locator.class);

		when(tile.locator(ContinenteSelectors.PACKAGE_QUANTITY)).thenReturn(qtyLoc);
		when(qtyLoc.first()).thenReturn(firstQtyLoc);
		when(firstQtyLoc.count()).thenReturn(1);
		when(firstQtyLoc.innerText()).thenReturn("emb. 500 gr");

		// Act
		String quantity = ContinenteParser.extractProductQuantity(tile);

		// Assert
		assertEquals("emb. 500 gr", quantity);
	}

	@Test
	void extractProductQuantity_missingQuantityElement_returnsNull() {
		// Arrange
		Locator tile = mock(Locator.class);
		Locator qtyLoc = mock(Locator.class);
		Locator firstQtyLoc = mock(Locator.class);

		when(tile.locator(ContinenteSelectors.PACKAGE_QUANTITY)).thenReturn(qtyLoc);
		when(qtyLoc.first()).thenReturn(firstQtyLoc);
		when(firstQtyLoc.count()).thenReturn(0);

		// Act
		String quantity = ContinenteParser.extractProductQuantity(tile);

		// Assert
		assertNull(quantity);
	}

	@Test
	void extractUnitPrice_secondaryLocContainsSlash_returnsSecondaryText() {
		// Arrange
		Locator tile = mock(Locator.class);
		Locator primaryLoc = mock(Locator.class);
		Locator firstPrimaryLoc = mock(Locator.class);
		Locator secondaryLoc = mock(Locator.class);
		Locator firstSecondaryLoc = mock(Locator.class);

		when(tile.locator(ContinenteSelectors.PRICE_PRIMARY)).thenReturn(primaryLoc);
		when(primaryLoc.first()).thenReturn(firstPrimaryLoc);
		when(firstPrimaryLoc.count()).thenReturn(1);
		when(firstPrimaryLoc.innerText()).thenReturn("1.49 €");

		when(tile.locator(ContinenteSelectors.PRICE_SECONDARY)).thenReturn(secondaryLoc);
		when(secondaryLoc.first()).thenReturn(firstSecondaryLoc);
		when(firstSecondaryLoc.count()).thenReturn(1);
		when(firstSecondaryLoc.innerText()).thenReturn("2.98 € / kg");

		// Act
		String unitPrice = ContinenteParser.extractUnitPrice(tile);

		// Assert
		assertEquals("2.98 € / kg", unitPrice);
	}

	@Test
	void extractPrices_invertedPrimaryKgSecondaryUn_invertsPrices() {
		// Arrange
		Locator tile = mock(Locator.class);
		Locator primaryLoc = mock(Locator.class);
		Locator firstPrimaryLoc = mock(Locator.class);
		Locator secondaryLoc = mock(Locator.class);
		Locator firstSecondaryLoc = mock(Locator.class);

		when(tile.locator(ContinenteSelectors.PRICE_PRIMARY)).thenReturn(primaryLoc);
		when(primaryLoc.first()).thenReturn(firstPrimaryLoc);
		when(firstPrimaryLoc.count()).thenReturn(1);
		when(firstPrimaryLoc.innerText()).thenReturn("6,49€/kg");

		when(tile.locator(ContinenteSelectors.PRICE_SECONDARY)).thenReturn(secondaryLoc);
		when(secondaryLoc.first()).thenReturn(firstSecondaryLoc);
		when(firstSecondaryLoc.count()).thenReturn(1);
		when(firstSecondaryLoc.innerText()).thenReturn("14,28€/un");

		// Act
		ContinenteParser.ParsedPrices prices = ContinenteParser.extractPrices(tile, "Peito de Frango em Vácuo");

		// Assert
		assertEquals(new BigDecimal("14.28"), prices.itemPrice());
		assertEquals("6,49€/kg", prices.unitPrice());
	}
}
