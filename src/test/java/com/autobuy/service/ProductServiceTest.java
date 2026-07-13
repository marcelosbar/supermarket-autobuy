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
		ProductMapping mapping = new ProductMapping("cheese", "CONTINENTE", "SKU-CHEESE", "Continente Cheese");
		mapping.setId(1L);
		when(productMappingRepository.findById(1L)).thenReturn(Optional.of(mapping));
		when(productMappingRepository.findBySearchTextAndSupermarketOrderByPriorityAsc("cheese", "CONTINENTE"))
				.thenReturn(List.of());

		productService.deleteMapping(1L);

		verify(productMappingRepository).delete(mapping);
		verify(productMappingRepository).findBySearchTextAndSupermarketOrderByPriorityAsc("cheese", "CONTINENTE");
	}

	@Test
	void testDeleteMapping_WithRemaining() {
		ProductMapping mapping = new ProductMapping("cheese", "CONTINENTE", "SKU-CHEESE", "Continente Cheese");
		mapping.setId(1L);
		mapping.setPriority(0);

		ProductMapping m1 = new ProductMapping("cheese", "CONTINENTE", "SKU-1", "Cheese 1");
		m1.setId(2L);
		m1.setPriority(1);

		when(productMappingRepository.findById(1L)).thenReturn(Optional.of(mapping));
		when(productMappingRepository.findBySearchTextAndSupermarketOrderByPriorityAsc("cheese", "CONTINENTE"))
				.thenReturn(List.of(m1));

		productService.deleteMapping(1L);

		verify(productMappingRepository).delete(mapping);
		verify(productMappingRepository).saveAndFlush(m1);
		assertEquals(0, m1.getPriority());
		assertNull(m1.getFallbackForId());
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
	void testFindMappingsBySearchTextAndSupermarket() {
		ProductMapping mapping = new ProductMapping("butter", "ALDI", "SKU-BUTTER", "Milbona Butter");
		when(productMappingRepository.findBySearchTextAndSupermarketOrderByPriorityAsc("butter", "ALDI"))
				.thenReturn(List.of(mapping));

		List<ProductMapping> found = productService.findMappingsBySearchTextAndSupermarket("butter", "ALDI");
		assertEquals(1, found.size());
		assertEquals("SKU-BUTTER", found.get(0).getExternalProductId());
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

	@Test
	void testSaveMappingWithPriority() {
		SearchResult result = new SearchResult("SKU-Alt", "Alt Product", "Brand", new BigDecimal("2.99"), "http://url",
				"Category");
		Product mockProduct = new Product("SKU-Alt", "CONTINENTE", "Alt Product", "Brand", "http://url", "Category");
		mockProduct.setId(2L);

		when(productRepository.findByExternalIdAndSupermarket("SKU-Alt", "CONTINENTE"))
				.thenReturn(Optional.of(mockProduct));

		ProductMapping primary = new ProductMapping("query", "CONTINENTE", "SKU-Prim", "Primary Product");
		primary.setId(10L);

		when(productMappingRepository.findBySearchTextAndSupermarketOrderByPriorityAsc("query", "CONTINENTE"))
				.thenReturn(List.of(primary));

		ProductMapping savedAlt = new ProductMapping("query", "CONTINENTE", "SKU-Alt", "Alt Product");
		savedAlt.setPriority(1);
		savedAlt.setFallbackForId(10L);

		when(productMappingRepository.save(any(ProductMapping.class))).thenReturn(savedAlt);

		productService.saveMappingWithPriority("query", "CONTINENTE", result, 1);

		verify(productRepository).findByExternalIdAndSupermarket("SKU-Alt", "CONTINENTE");
		verify(productMappingRepository).save(any(ProductMapping.class));
	}

	@Test
	void testSaveMappingWithPriority_DuplicateSku() {
		SearchResult result = new SearchResult("SKU-1", "Name", "Brand", new BigDecimal("1.99"), "http://url",
				"Category");
		Product mockProduct = new Product("SKU-1", "CONTINENTE", "Name", "Brand", "http://url", "Category");
		mockProduct.setId(1L);

		when(productRepository.findByExternalIdAndSupermarket("SKU-1", "CONTINENTE"))
				.thenReturn(Optional.of(mockProduct));

		ProductMapping primary = new ProductMapping("query", "CONTINENTE", "SKU-1", "Name");
		primary.setId(10L);
		primary.setPriority(0);

		when(productMappingRepository.findBySearchTextAndSupermarketOrderByPriorityAsc("query", "CONTINENTE"))
				.thenReturn(List.of(primary));

		productService.saveMappingWithPriority("query", "CONTINENTE", result, 1);

		verify(productRepository).findByExternalIdAndSupermarket("SKU-1", "CONTINENTE");
		verify(productMappingRepository, never()).save(any(ProductMapping.class));
	}

	@Test
	void testPromoteMapping() {
		ProductMapping m0 = new ProductMapping("query", "CONTINENTE", "SKU-0", "Product 0");
		m0.setId(10L);
		m0.setPriority(0);

		ProductMapping m1 = new ProductMapping("query", "CONTINENTE", "SKU-1", "Product 1");
		m1.setId(11L);
		m1.setPriority(1);
		m1.setFallbackForId(10L);

		when(productMappingRepository.findById(11L)).thenReturn(Optional.of(m1));
		when(productMappingRepository.findBySearchTextAndSupermarketOrderByPriorityAsc("query", "CONTINENTE"))
				.thenReturn(List.of(m0, m1));

		productService.promoteMapping(11L);

		verify(productMappingRepository, atLeastOnce()).saveAndFlush(m1);
		verify(productMappingRepository, atLeastOnce()).saveAndFlush(m0);
	}

	@Test
	void testDemoteMapping() {
		ProductMapping m0 = new ProductMapping("query", "CONTINENTE", "SKU-0", "Product 0");
		m0.setId(10L);
		m0.setPriority(0);

		ProductMapping m1 = new ProductMapping("query", "CONTINENTE", "SKU-1", "Product 1");
		m1.setId(11L);
		m1.setPriority(1);
		m1.setFallbackForId(10L);

		when(productMappingRepository.findById(10L)).thenReturn(Optional.of(m0));
		when(productMappingRepository.findBySearchTextAndSupermarketOrderByPriorityAsc("query", "CONTINENTE"))
				.thenReturn(List.of(m0, m1));

		productService.demoteMapping(10L);

		verify(productMappingRepository, atLeastOnce()).saveAndFlush(m0);
		verify(productMappingRepository, atLeastOnce()).saveAndFlush(m1);
	}

	@Test
	void testDeleteMapping_Nonexistent() {
		when(productMappingRepository.findById(99L)).thenReturn(Optional.empty());
		productService.deleteMapping(99L);
		verify(productMappingRepository, never()).delete(any());
	}

	@Test
	void testPromoteMapping_Nonexistent() {
		when(productMappingRepository.findById(99L)).thenReturn(Optional.empty());
		productService.promoteMapping(99L);
		verify(productMappingRepository, never()).saveAndFlush(any());
	}

	@Test
	void testPromoteMapping_AlreadyPrimary() {
		ProductMapping m0 = new ProductMapping("query", "CONTINENTE", "SKU-0", "Product 0");
		m0.setId(10L);
		m0.setPriority(0);

		when(productMappingRepository.findById(10L)).thenReturn(Optional.of(m0));
		productService.promoteMapping(10L);
		verify(productMappingRepository, never()).saveAndFlush(any());
	}

	@Test
	void testPromoteMapping_NoAboveElement() {
		ProductMapping m1 = new ProductMapping("query", "CONTINENTE", "SKU-1", "Product 1");
		m1.setId(11L);
		m1.setPriority(2); // gap, no priority 1 above

		ProductMapping m0 = new ProductMapping("query", "CONTINENTE", "SKU-0", "Product 0");
		m0.setId(10L);
		m0.setPriority(0);

		when(productMappingRepository.findById(11L)).thenReturn(Optional.of(m1));
		when(productMappingRepository.findBySearchTextAndSupermarketOrderByPriorityAsc("query", "CONTINENTE"))
				.thenReturn(List.of(m0, m1));

		productService.promoteMapping(11L);
		verify(productMappingRepository, never()).saveAndFlush(any());
	}

	@Test
	void testDemoteMapping_Nonexistent() {
		when(productMappingRepository.findById(99L)).thenReturn(Optional.empty());
		productService.demoteMapping(99L);
		verify(productMappingRepository, never()).saveAndFlush(any());
	}

	@Test
	void testDemoteMapping_AlreadyAtBottom() {
		ProductMapping m0 = new ProductMapping("query", "CONTINENTE", "SKU-0", "Product 0");
		m0.setId(10L);
		m0.setPriority(0);

		when(productMappingRepository.findById(10L)).thenReturn(Optional.of(m0));
		when(productMappingRepository.findBySearchTextAndSupermarketOrderByPriorityAsc("query", "CONTINENTE"))
				.thenReturn(List.of(m0));

		productService.demoteMapping(10L);
		verify(productMappingRepository, never()).saveAndFlush(any());
	}

	@Test
	void testDemoteMapping_NoBelowElement() {
		ProductMapping m0 = new ProductMapping("query", "CONTINENTE", "SKU-0", "Product 0");
		m0.setId(10L);
		m0.setPriority(0);

		ProductMapping m2 = new ProductMapping("query", "CONTINENTE", "SKU-2", "Product 2");
		m2.setId(12L);
		m2.setPriority(2); // gap, no priority 1 below m0

		when(productMappingRepository.findById(10L)).thenReturn(Optional.of(m0));
		when(productMappingRepository.findBySearchTextAndSupermarketOrderByPriorityAsc("query", "CONTINENTE"))
				.thenReturn(List.of(m0, m2));

		productService.demoteMapping(10L);
		verify(productMappingRepository, never()).saveAndFlush(any());
	}
}
