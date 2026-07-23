package com.autobuy.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ProductTest {

	@Test
	void equals_sameInstance_returnsTrue() {
		// Arrange
		Product product = new Product("ext-101", "continente", "Leite", "Mimosa", "http://example.com", "Lacticínios");

		// Act & Assert
		assertEquals(product, product);
	}

	@Test
	void equals_sameBusinessKey_returnsTrue() {
		// Arrange
		Product product1 = new Product("ext-101", "continente", "Leite 1L", "Mimosa", "http://example.com/1",
				"Lacticínios");
		Product product2 = new Product("ext-101", "continente", "Leite Meio Gordo 1L", "Mimosa Brand",
				"http://example.com/2", "Leites");

		// Act & Assert
		assertEquals(product1, product2);
		assertEquals(product2, product1); // symmetric
	}

	@Test
	void equals_differentBusinessKey_returnsFalse() {
		// Arrange
		Product product1 = new Product("ext-101", "continente", "Leite", "Mimosa", "http://example.com", "Lacticínios");
		Product product2 = new Product("ext-102", "continente", "Leite", "Mimosa", "http://example.com", "Lacticínios");
		Product product3 = new Product("ext-101", "pingo_doce", "Leite", "Mimosa", "http://example.com", "Lacticínios");

		// Act & Assert
		assertNotEquals(product1, product2);
		assertNotEquals(product1, product3);
	}

	@Test
	void equals_nullOrDifferentClass_returnsFalse() {
		// Arrange
		Product product = new Product("ext-101", "continente", "Leite", "Mimosa", "http://example.com", "Lacticínios");

		// Act & Assert
		assertFalse(product.equals(null));
		assertFalse(product.equals("Not A Product"));
	}

	@Test
	void equals_transitiveContract_holdsTrue() {
		// Arrange
		Product product1 = new Product("ext-101", "continente", "Name 1", "Brand 1", "url1", "cat1");
		Product product2 = new Product("ext-101", "continente", "Name 2", "Brand 2", "url2", "cat2");
		Product product3 = new Product("ext-101", "continente", "Name 3", "Brand 3", "url3", "cat3");

		// Act & Assert
		assertEquals(product1, product2);
		assertEquals(product2, product3);
		assertEquals(product1, product3);
	}

	@Test
	void hashCode_sameBusinessKey_returnsSameHashCode() {
		// Arrange
		Product product1 = new Product("ext-101", "continente", "Leite 1L", "Mimosa", "http://example.com/1",
				"Lacticínios");
		Product product2 = new Product("ext-101", "continente", "Leite Meio Gordo 1L", "Mimosa Brand",
				"http://example.com/2", "Leites");

		// Act & Assert
		assertEquals(product1.hashCode(), product2.hashCode());
	}
}
