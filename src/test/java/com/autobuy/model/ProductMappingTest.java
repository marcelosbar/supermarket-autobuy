package com.autobuy.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ProductMappingTest {

	@Test
	void equals_sameInstance_returnsTrue() {
		// Arrange
		ProductMapping mapping = new ProductMapping("leite gordo", "continente", "ext-123", "Leite Gordo 1L");

		// Act & Assert
		assertEquals(mapping, mapping);
	}

	@Test
	void equals_sameBusinessKey_returnsTrue() {
		// Arrange
		ProductMapping mapping1 = new ProductMapping("leite gordo", "continente", "ext-123", "Leite Gordo 1L");
		ProductMapping mapping2 = new ProductMapping("leite gordo", "continente", "ext-999", "Leite Ultra Gordo");

		// Act & Assert
		assertEquals(mapping1, mapping2);
		assertEquals(mapping2, mapping1); // symmetric
	}

	@Test
	void equals_differentBusinessKey_returnsFalse() {
		// Arrange
		ProductMapping mapping1 = new ProductMapping("leite gordo", "continente", "ext-123", "Leite Gordo 1L");
		ProductMapping mapping2 = new ProductMapping("leite magro", "continente", "ext-123", "Leite Gordo 1L");
		ProductMapping mapping3 = new ProductMapping("leite gordo", "pingo_doce", "ext-123", "Leite Gordo 1L");
		ProductMapping mapping4 = new ProductMapping("leite gordo", "continente", "ext-123", "Leite Gordo 1L");
		mapping4.setPriority(1);

		// Act & Assert
		assertNotEquals(mapping1, mapping2);
		assertNotEquals(mapping1, mapping3);
		assertNotEquals(mapping1, mapping4);
	}

	@Test
	void equals_nullOrDifferentClass_returnsFalse() {
		// Arrange
		ProductMapping mapping = new ProductMapping("leite gordo", "continente", "ext-123", "Leite Gordo 1L");

		// Act & Assert
		assertFalse(mapping.equals(null));
		assertFalse(mapping.equals("Not A Mapping"));
	}

	@Test
	void equals_transitiveContract_holdsTrue() {
		// Arrange
		ProductMapping mapping1 = new ProductMapping("leite gordo", "continente", "ext-1", "Name 1");
		ProductMapping mapping2 = new ProductMapping("leite gordo", "continente", "ext-2", "Name 2");
		ProductMapping mapping3 = new ProductMapping("leite gordo", "continente", "ext-3", "Name 3");

		// Act & Assert
		assertEquals(mapping1, mapping2);
		assertEquals(mapping2, mapping3);
		assertEquals(mapping1, mapping3);
	}

	@Test
	void hashCode_sameBusinessKey_returnsSameHashCode() {
		// Arrange
		ProductMapping mapping1 = new ProductMapping("leite gordo", "continente", "ext-123", "Leite Gordo 1L");
		ProductMapping mapping2 = new ProductMapping("leite gordo", "continente", "ext-999", "Leite Ultra Gordo");

		// Act & Assert
		assertEquals(mapping1.hashCode(), mapping2.hashCode());
	}
}
