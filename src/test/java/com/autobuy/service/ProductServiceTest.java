package com.autobuy.service;

import com.autobuy.model.Product;
import com.autobuy.model.ProductMapping;
import com.autobuy.repository.ProductMappingRepository;
import com.autobuy.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class ProductServiceTest {

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private ProductMappingRepository productMappingRepository;

	private ProductService productService;

	@BeforeEach
	void setUp() {
		productService = new ProductService(productRepository, productMappingRepository);
	}

	@Test
	void testSaveMapping() {
		ProductMapping mapping = new ProductMapping("milk", "CONTINENTE", "SKU-MILK", "Mimosa Milk");
		ProductMapping saved = productService.saveMapping(mapping);

		assertNotNull(saved.getId());
		assertEquals("milk", saved.getSearchText());
		assertEquals("CONTINENTE", saved.getSupermarket());
		assertEquals("SKU-MILK", saved.getExternalProductId());
		assertEquals("Mimosa Milk", saved.getProductName());
	}

	@Test
	void testFindMapping() {
		ProductMapping mapping = new ProductMapping("bread", "CONTINENTE", "SKU-BREAD", "Continente Bread");
		productMappingRepository.save(mapping);

		Optional<ProductMapping> found = productService.findMapping("CONTINENTE", "SKU-BREAD");
		assertTrue(found.isPresent());
		assertEquals("bread", found.get().getSearchText());

		Optional<ProductMapping> notFound = productService.findMapping("ALDI", "SKU-BREAD");
		assertFalse(notFound.isPresent());
	}

	@Test
	void testDeleteMapping() {
		ProductMapping mapping = new ProductMapping("eggs", "CONTINENTE", "SKU-EGGS", "Continente Eggs");
		ProductMapping saved = productMappingRepository.save(mapping);

		assertTrue(productMappingRepository.findById(saved.getId()).isPresent());
		productService.deleteMapping(saved.getId());
		assertFalse(productMappingRepository.findById(saved.getId()).isPresent());
	}

	@Test
	void testFindOrCreateProduct_DefaultSupermarket() {
		// New product creation
		Product product1 = productService.findOrCreateProduct("Matinados Eggs", "EAN-12345", "Matinados");
		assertNotNull(product1.getId());
		assertEquals("CONTINENTE", product1.getSupermarket());
		assertEquals("EAN-12345", product1.getExternalId());
		assertEquals("Matinados Eggs", product1.getName());
		assertEquals("Matinados", product1.getBrand());

		// Return existing product
		Product product2 = productService.findOrCreateProduct("Matinados Eggs", "EAN-12345", "Matinados");
		assertEquals(product1.getId(), product2.getId());
	}

	@Test
	void testFindOrCreateProduct_Overloaded() {
		// New product creation
		Product product1 = productService.findOrCreateProduct("SKU-999", "ALDI", "Aldi Milk", "Milbona",
				"http://aldi/milk", "Dairy");
		assertNotNull(product1.getId());
		assertEquals("SKU-999", product1.getExternalId());
		assertEquals("ALDI", product1.getSupermarket());
		assertEquals("Aldi Milk", product1.getName());
		assertEquals("Milbona", product1.getBrand());
		assertEquals("http://aldi/milk", product1.getUrl());
		assertEquals("Dairy", product1.getCategory());

		// Return existing product
		Product product2 = productService.findOrCreateProduct("SKU-999", "ALDI", "Aldi Milk", "Milbona",
				"http://aldi/milk", "Dairy");
		assertEquals(product1.getId(), product2.getId());
	}

	@Test
	void testFindMappingBySearchTextAndSupermarket() {
		ProductMapping mapping = new ProductMapping("butter", "ALDI", "SKU-BUTTER", "Milbona Butter");
		productMappingRepository.save(mapping);

		Optional<ProductMapping> found = productService.findMappingBySearchTextAndSupermarket("butter", "ALDI");
		assertTrue(found.isPresent());
		assertEquals("SKU-BUTTER", found.get().getExternalProductId());
	}

	@Test
	void testFindMappingById() {
		ProductMapping mapping = new ProductMapping("cheese", "CONTINENTE", "SKU-CHEESE", "Continente Cheese");
		ProductMapping saved = productMappingRepository.save(mapping);

		Optional<ProductMapping> found = productService.findMappingById(saved.getId());
		assertTrue(found.isPresent());
		assertEquals("cheese", found.get().getSearchText());
	}

	@Test
	void testFindAllMappings() {
		productMappingRepository.deleteAll();
		ProductMapping mapping1 = new ProductMapping("cheese", "CONTINENTE", "SKU-CHEESE", "Continente Cheese");
		ProductMapping mapping2 = new ProductMapping("butter", "ALDI", "SKU-BUTTER", "Milbona Butter");
		productMappingRepository.saveAll(List.of(mapping1, mapping2));

		List<ProductMapping> all = productService.findAllMappings();
		assertEquals(2, all.size());
	}
}
