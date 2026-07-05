package com.autobuy.driver.continente;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ContinenteParserTest {

	@Test
	void testParsePrice_StandardEuropean() {
		assertEquals(new BigDecimal("1.49"), ContinenteParser.parsePrice("1,49 €"));
		assertEquals(new BigDecimal("1.49"), ContinenteParser.parsePrice("1,49€"));
		assertEquals(new BigDecimal("1.49"), ContinenteParser.parsePrice("1.49 €"));
	}

	@Test
	void testParsePrice_StandardUS() {
		assertEquals(new BigDecimal("10.99"), ContinenteParser.parsePrice("€ 10.99"));
		assertEquals(new BigDecimal("10.99"), ContinenteParser.parsePrice("10.99"));
	}

	@Test
	void testParsePrice_LargeNumbers() {
		assertEquals(new BigDecimal("1250.45"), ContinenteParser.parsePrice("1.250,45 €"));
	}

	@Test
	void testParsePrice_LeadingDecimalDot() {
		assertEquals(new BigDecimal("0.89"), ContinenteParser.parsePrice(".89 €"));
		assertEquals(new BigDecimal("0.89"), ContinenteParser.parsePrice(",89 €"));
	}

	@Test
	void testParsePrice_EmptyAndNull() {
		assertEquals(BigDecimal.ZERO, ContinenteParser.parsePrice(""));
		assertEquals(BigDecimal.ZERO, ContinenteParser.parsePrice("   "));
		assertEquals(BigDecimal.ZERO, ContinenteParser.parsePrice(null));
	}

	@Test
	void testParsePrice_InvalidFormats() {
		assertEquals(BigDecimal.ZERO, ContinenteParser.parsePrice("price unknown"));
		assertEquals(BigDecimal.ZERO, ContinenteParser.parsePrice("abc"));
	}
}
