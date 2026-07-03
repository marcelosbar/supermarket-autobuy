package com.autobuy.service;

import com.autobuy.model.Product;
import com.autobuy.model.ProductMapping;
import com.autobuy.model.SearchResult;
import com.autobuy.repository.ProductMappingRepository;
import com.autobuy.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

	@Mock
	private ProductRepository productRepository;

	@Mock
	private ProductMappingRepository productMappingRepository;

	@InjectMocks
	private ProductService productService;

	@Test
	void testSaveMapping() {
		ProductMapping mapping = new ProductMapping("milk", "CONTINENTE", "SKU-MILK", "Mimosa Milk");
		ProductMapping savedMapping = new ProductMapping("milk", "CONTINENTE", "SKU-MILK", "Mimosa Milk");
		savedMapping.setId(1L);

		when(productMappingRepository.save(mapping)).thenReturn(savedMapping);

		ProductMapping saved = productService.saveMapping(mapping);

		assertNotNull(saved);
		assertNotNull(saved.getId());
		assertEquals("milk", saved.getSearchText());
		assertEquals("CONTINENTE", saved.getSupermarket());
		assertEquals("SKU-MILK", saved.getExternalProductId());
		assertEquals("Mimosa Milk", saved.getProductName());
		verify(productMappingRepository).save(mapping);
	}

	@Test
	void testFindMapping() {
		ProductMapping mapping = new ProductMapping("bread", "CONTINENTE", "SKU-BREAD", "Continente Bread");
		when(productMappingRepository.findBySupermarketAndExternalProductId("CONTINENTE", "SKU-BREAD"))
				.thenReturn(Optional.of(mapping));
		when(productMappingRepository.findBySupermarketAndExternalProductId("ALDI", "SKU-BREAD"))
				.thenReturn(Optional.empty());

		Optional<ProductMapping> found = productService.findMapping("CONTINENTE", "SKU-BREAD");
		assertTrue(found.isPresent());
		assertEquals("bread", found.get().getSearchText());

		Optional<ProductMapping> notFound = productService.findMapping("ALDI", "SKU-BREAD");
		assertFalse(notFound.isPresent());
	}

	@Test
	void testDeleteMapping() {
		productService.deleteMapping(1L);
		verify(productMappingRepository).deleteById(1L);
	}

	@Test
	void testFindOrCreateProduct_DefaultSupermarket() {
		// New product creation path
		when(productRepository.findByExternalIdAndSupermarket("EAN-12345", "CONTINENTE")).thenReturn(Optional.empty());
		Product mockProduct = new Product("EAN-12345", "CONTINENTE", "Matinados Eggs", "Matinados", null, null);
		mockProduct.setId(1L);
		when(productRepository.save(any(Product.class))).thenReturn(mockProduct);

		Product product1 = productService.findOrCreateProduct("EAN-12345", "CONTINENTE", "Matinados Eggs", "Matinados",
				null, null);
		assertNotNull(product1.getId());
		assertEquals("CONTINENTE", product1.getSupermarket());
		assertEquals("EAN-12345", product1.getExternalId());
		assertEquals("Matinados Eggs", product1.getName());
		assertEquals("Matinados", product1.getBrand());

		// Return existing product path
		when(productRepository.findByExternalIdAndSupermarket("EAN-12345", "CONTINENTE"))
				.thenReturn(Optional.of(mockProduct));
		Product product2 = productService.findOrCreateProduct("EAN-12345", "CONTINENTE", "Matinados Eggs", "Matinados",
				null, null);
		assertEquals(product1.getId(), product2.getId());
	}

	@Test
	void testFindOrCreateProduct_Overloaded() {
		// New product creation path
		when(productRepository.findByExternalIdAndSupermarket("SKU-999", "ALDI")).thenReturn(Optional.empty());
		Product mockProduct = new Product("SKU-999", "ALDI", "Aldi Milk", "Milbona", "http://aldi/milk", "Dairy");
		mockProduct.setId(1L);
		when(productRepository.save(any(Product.class))).thenReturn(mockProduct);

		Product product1 = productService.findOrCreateProduct("SKU-999", "ALDI", "Aldi Milk", "Milbona",
				"http://aldi/milk", "Dairy");
		assertNotNull(product1.getId());
		assertEquals("SKU-999", product1.getExternalId());
		assertEquals("ALDI", product1.getSupermarket());
		assertEquals("Aldi Milk", product1.getName());
		assertEquals("Milbona", product1.getBrand());
		assertEquals("http://aldi/milk", product1.getUrl());
		assertEquals("Dairy", product1.getCategory());

		// Return existing product path
		when(productRepository.findByExternalIdAndSupermarket("SKU-999", "ALDI")).thenReturn(Optional.of(mockProduct));
		Product product2 = productService.findOrCreateProduct("SKU-999", "ALDI", "Aldi Milk", "Milbona",
				"http://aldi/milk", "Dairy");
		assertEquals(product1.getId(), product2.getId());
	}

	@Test
	void testFindMappingBySearchTextAndSupermarket() {
		ProductMapping mapping = new ProductMapping("butter", "ALDI", "SKU-BUTTER", "Milbona Butter");
		when(productMappingRepository.findBySearchTextAndSupermarket("butter", "ALDI"))
				.thenReturn(Optional.of(mapping));

		Optional<ProductMapping> found = productService.findMappingBySearchTextAndSupermarket("butter", "ALDI");
		assertTrue(found.isPresent());
		assertEquals("SKU-BUTTER", found.get().getExternalProductId());
	}

	@Test
	void testFindMappingById() {
		ProductMapping mapping = new ProductMapping("cheese", "CONTINENTE", "SKU-CHEESE", "Continente Cheese");
		when(productMappingRepository.findById(1L)).thenReturn(Optional.of(mapping));

		Optional<ProductMapping> found = productService.findMappingById(1L);
		assertTrue(found.isPresent());
		assertEquals("cheese", found.get().getSearchText());
	}

	@Test
	void testFindAllMappings() {
		ProductMapping mapping1 = new ProductMapping("cheese", "CONTINENTE", "SKU-CHEESE", "Continente Cheese");
		ProductMapping mapping2 = new ProductMapping("butter", "ALDI", "SKU-BUTTER", "Milbona Butter");
		when(productMappingRepository.findAll()).thenReturn(List.of(mapping1, mapping2));

		List<ProductMapping> all = productService.findAllMappings();
		assertEquals(2, all.size());
	}

	@Test
	void testSaveMappingOrchestrated() {
		SearchResult result = new SearchResult("SKU-1", "Name", "Brand", new BigDecimal("1.99"), "http://url",
				"Category");
		Product mockProduct = new Product("SKU-1", "CONTINENTE", "Name", "Brand", "http://url", "Category");
		mockProduct.setId(1L);

		when(productRepository.findByExternalIdAndSupermarket("SKU-1", "CONTINENTE"))
				.thenReturn(Optional.of(mockProduct));

		ProductMapping mapping = new ProductMapping("query", "CONTINENTE", "SKU-1", "Name");
		when(productMappingRepository.save(any(ProductMapping.class))).thenReturn(mapping);

		productService.saveMapping("query", "CONTINENTE", result);

		verify(productRepository).findByExternalIdAndSupermarket("SKU-1", "CONTINENTE");
		verify(productMappingRepository).save(any(ProductMapping.class));
	}
}
