package com.autobuy.model;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class PriceHistoryTest {

	@Test
	void equals_sameInstance_returnsTrue() {
		// Arrange
		Product product = new Product("ext-101", "continente", "Leite", "Mimosa", "http://example.com", "Lacticínios");
		LocalDateTime now = LocalDateTime.now();
		PriceHistory history = new PriceHistory(product, new BigDecimal("1.29"), now, "SCRAPER");

		// Act & Assert
		assertEquals(history, history);
	}

	@Test
	void equals_sameBusinessKey_returnsTrue() {
		// Arrange
		Product product = new Product("ext-101", "continente", "Leite", "Mimosa", "http://example.com", "Lacticínios");
		LocalDateTime now = LocalDateTime.of(2026, 7, 23, 10, 0);
		PriceHistory history1 = new PriceHistory(product, new BigDecimal("1.29"), now, "SCRAPER");
		PriceHistory history2 = new PriceHistory(product, new BigDecimal("1.49"), now, "MANUAL_UPDATE");

		// Act & Assert
		assertEquals(history1, history2);
		assertEquals(history2, history1); // symmetric
	}

	@Test
	void equals_differentBusinessKey_returnsFalse() {
		// Arrange
		Product product1 = new Product("ext-101", "continente", "Leite", "Mimosa", "http://example.com", "Lacticínios");
		Product product2 = new Product("ext-102", "continente", "Leite", "Mimosa", "http://example.com", "Lacticínios");
		LocalDateTime now = LocalDateTime.of(2026, 7, 23, 10, 0);
		LocalDateTime later = LocalDateTime.of(2026, 7, 23, 11, 0);

		PriceHistory history1 = new PriceHistory(product1, new BigDecimal("1.29"), now, "SCRAPER");
		PriceHistory history2 = new PriceHistory(product2, new BigDecimal("1.29"), now, "SCRAPER");
		PriceHistory history3 = new PriceHistory(product1, new BigDecimal("1.29"), later, "SCRAPER");

		// Act & Assert
		assertNotEquals(history1, history2);
		assertNotEquals(history1, history3);
	}

	@Test
	void equals_nullOrDifferentClass_returnsFalse() {
		// Arrange
		Product product = new Product("ext-101", "continente", "Leite", "Mimosa", "http://example.com", "Lacticínios");
		PriceHistory history = new PriceHistory(product, new BigDecimal("1.29"), LocalDateTime.now(), "SCRAPER");

		// Act & Assert
		assertNotEquals(null, history);
		assertNotEquals(history, "Not Price History");
	}

	@Test
	void equals_transitiveContract_holdsTrue() {
		// Arrange
		Product product = new Product("ext-101", "continente", "Leite", "Mimosa", "http://example.com", "Lacticínios");
		LocalDateTime timestamp = LocalDateTime.of(2026, 7, 23, 12, 0);

		PriceHistory history1 = new PriceHistory(product, new BigDecimal("1.00"), timestamp, "Source 1");
		PriceHistory history2 = new PriceHistory(product, new BigDecimal("2.00"), timestamp, "Source 2");
		PriceHistory history3 = new PriceHistory(product, new BigDecimal("3.00"), timestamp, "Source 3");

		// Act & Assert
		assertEquals(history1, history2);
		assertEquals(history2, history3);
		assertEquals(history1, history3);
	}

	@Test
	void hashCode_sameBusinessKey_returnsSameHashCode() {
		// Arrange
		Product product = new Product("ext-101", "continente", "Leite", "Mimosa", "http://example.com", "Lacticínios");
		LocalDateTime now = LocalDateTime.of(2026, 7, 23, 10, 0);

		PriceHistory history1 = new PriceHistory(product, new BigDecimal("1.29"), now, "SCRAPER");
		PriceHistory history2 = new PriceHistory(product, new BigDecimal("1.49"), now, "MANUAL_UPDATE");

		// Act & Assert
		assertEquals(history1.hashCode(), history2.hashCode());
	}
}
