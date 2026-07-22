package com.autobuy.driver.continente;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
