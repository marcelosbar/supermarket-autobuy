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
	void saveMapping_validInput_persistsMapping() {
		// Arrange
		ProductMapping mapping = new ProductMapping("milk", "CONTINENTE", "SKU-MILK", "Mimosa Milk");
		ProductMapping savedMapping = new ProductMapping("milk", "CONTINENTE", "SKU-MILK", "Mimosa Milk");
		savedMapping.setId(1L);

		when(productMappingRepository.save(mapping)).thenReturn(savedMapping);

		// Act
		ProductMapping saved = productService.saveMapping(mapping);

		// Assert
		assertNotNull(saved);
		assertNotNull(saved.getId());
		assertEquals("milk", saved.getSearchText());
		assertEquals("CONTINENTE", saved.getSupermarket());
		assertEquals("SKU-MILK", saved.getExternalProductId());
		assertEquals("Mimosa Milk", saved.getProductName());
		verify(productMappingRepository).save(mapping);
	}

	@Test
	void findMapping_supermarketAndSku_returnsOptional() {
		// Arrange
		ProductMapping mapping = new ProductMapping("bread", "CONTINENTE", "SKU-BREAD", "Continente Bread");
		when(productMappingRepository.findBySupermarketAndExternalProductId("CONTINENTE", "SKU-BREAD"))
				.thenReturn(Optional.of(mapping));
		when(productMappingRepository.findBySupermarketAndExternalProductId("ALDI", "SKU-BREAD"))
				.thenReturn(Optional.empty());

		// Act
		Optional<ProductMapping> found = productService.findMapping("CONTINENTE", "SKU-BREAD");
		Optional<ProductMapping> notFound = productService.findMapping("ALDI", "SKU-BREAD");

		// Assert
		assertTrue(found.isPresent());
		assertEquals("bread", found.get().getSearchText());
		assertFalse(notFound.isPresent());
	}

	@Test
	void deleteMapping_existingId_deletesMapping() {
		// Arrange
		ProductMapping mapping = new ProductMapping("cheese", "CONTINENTE", "SKU-CHEESE", "Continente Cheese");
		mapping.setId(1L);
		when(productMappingRepository.findById(1L)).thenReturn(Optional.of(mapping));
		when(productMappingRepository.findBySearchTextAndSupermarketOrderByPriorityAsc("cheese", "CONTINENTE"))
				.thenReturn(List.of());

		// Act
		productService.deleteMapping(1L);

		// Assert
		verify(productMappingRepository).delete(mapping);
		verify(productMappingRepository).findBySearchTextAndSupermarketOrderByPriorityAsc("cheese", "CONTINENTE");
	}

	@Test
	void deleteMapping_withRemainingMappings_reordersPriorities() {
		// Arrange
		ProductMapping mapping = new ProductMapping("cheese", "CONTINENTE", "SKU-CHEESE", "Continente Cheese");
		mapping.setId(1L);
		mapping.setPriority(0);

		ProductMapping m1 = new ProductMapping("cheese", "CONTINENTE", "SKU-1", "Cheese 1");
		m1.setId(2L);
		m1.setPriority(1);

		when(productMappingRepository.findById(1L)).thenReturn(Optional.of(mapping));
		when(productMappingRepository.findBySearchTextAndSupermarketOrderByPriorityAsc("cheese", "CONTINENTE"))
				.thenReturn(List.of(m1));

		// Act
		productService.deleteMapping(1L);

		// Assert
		verify(productMappingRepository).delete(mapping);
		verify(productMappingRepository).saveAndFlush(m1);
		assertEquals(0, m1.getPriority());
		assertNull(m1.getFallbackForId());
	}

	@Test
	void findOrCreateProduct_newAndExistingProduct_createsOrReturnsProduct() {
		// Arrange (New product creation path)
		when(productRepository.findByExternalIdAndSupermarket("EAN-12345", "CONTINENTE")).thenReturn(Optional.empty());
		Product mockProduct = new Product("EAN-12345", "CONTINENTE", "Matinados Eggs", "Matinados", null, null);
		mockProduct.setId(1L);
		when(productRepository.save(any(Product.class))).thenReturn(mockProduct);

		// Act
		Product product1 = productService.findOrCreateProduct("EAN-12345", "CONTINENTE", "Matinados Eggs", "Matinados",
				null, null);

		// Assert
		assertNotNull(product1.getId());
		assertEquals("CONTINENTE", product1.getSupermarket());
		assertEquals("EAN-12345", product1.getExternalId());
		assertEquals("Matinados Eggs", product1.getName());
		assertEquals("Matinados", product1.getBrand());

		// Arrange (Return existing product path)
		when(productRepository.findByExternalIdAndSupermarket("EAN-12345", "CONTINENTE"))
				.thenReturn(Optional.of(mockProduct));

		// Act
		Product product2 = productService.findOrCreateProduct("EAN-12345", "CONTINENTE", "Matinados Eggs", "Matinados",
				null, null);

		// Assert
		assertEquals(product1.getId(), product2.getId());
	}

	@Test
	void findOrCreateProduct_overloadedParameters_createsOrReturnsProduct() {
		// Arrange (New product creation path)
		when(productRepository.findByExternalIdAndSupermarket("SKU-999", "ALDI")).thenReturn(Optional.empty());
		Product mockProduct = new Product("SKU-999", "ALDI", "Aldi Milk", "Milbona", "http://aldi/milk", "Dairy");
		mockProduct.setId(1L);
		when(productRepository.save(any(Product.class))).thenReturn(mockProduct);

		// Act
		Product product1 = productService.findOrCreateProduct("SKU-999", "ALDI", "Aldi Milk", "Milbona",
				"http://aldi/milk", "Dairy");

		// Assert
		assertNotNull(product1.getId());
		assertEquals("SKU-999", product1.getExternalId());
		assertEquals("ALDI", product1.getSupermarket());
		assertEquals("Aldi Milk", product1.getName());
		assertEquals("Milbona", product1.getBrand());
		assertEquals("http://aldi/milk", product1.getUrl());
		assertEquals("Dairy", product1.getCategory());

		// Arrange (Return existing product path)
		when(productRepository.findByExternalIdAndSupermarket("SKU-999", "ALDI")).thenReturn(Optional.of(mockProduct));

		// Act
		Product product2 = productService.findOrCreateProduct("SKU-999", "ALDI", "Aldi Milk", "Milbona",
				"http://aldi/milk", "Dairy");

		// Assert
		assertEquals(product1.getId(), product2.getId());
	}

	@Test
	void findMappingsBySearchTextAndSupermarket_validQuery_returnsMappings() {
		// Arrange
		ProductMapping mapping = new ProductMapping("butter", "ALDI", "SKU-BUTTER", "Milbona Butter");
		when(productMappingRepository.findBySearchTextAndSupermarketOrderByPriorityAsc("butter", "ALDI"))
				.thenReturn(List.of(mapping));

		// Act
		List<ProductMapping> found = productService.findMappingsBySearchTextAndSupermarket("butter", "ALDI");

		// Assert
		assertEquals(1, found.size());
		assertEquals("SKU-BUTTER", found.get(0).getExternalProductId());
	}

	@Test
	void findMappingById_existingId_returnsMapping() {
		// Arrange
		ProductMapping mapping = new ProductMapping("cheese", "CONTINENTE", "SKU-CHEESE", "Continente Cheese");
		when(productMappingRepository.findById(1L)).thenReturn(Optional.of(mapping));

		// Act
		Optional<ProductMapping> found = productService.findMappingById(1L);

		// Assert
		assertTrue(found.isPresent());
		assertEquals("cheese", found.get().getSearchText());
	}

	@Test
	void findAllMappings_existingMappings_returnsAllMappings() {
		// Arrange
		ProductMapping mapping1 = new ProductMapping("cheese", "CONTINENTE", "SKU-CHEESE", "Continente Cheese");
		ProductMapping mapping2 = new ProductMapping("butter", "ALDI", "SKU-BUTTER", "Milbona Butter");
		when(productMappingRepository.findAll()).thenReturn(List.of(mapping1, mapping2));

		// Act
		List<ProductMapping> all = productService.findAllMappings();

		// Assert
		assertEquals(2, all.size());
	}

	@Test
	void saveMapping_searchResult_createsProductAndMapping() {
		// Arrange
		SearchResult result = new SearchResult("SKU-1", "Name", "Brand", new BigDecimal("1.99"), "http://url",
				"Category");
		Product mockProduct = new Product("SKU-1", "CONTINENTE", "Name", "Brand", "http://url", "Category");
		mockProduct.setId(1L);

		when(productRepository.findByExternalIdAndSupermarket("SKU-1", "CONTINENTE"))
				.thenReturn(Optional.of(mockProduct));

		ProductMapping mapping = new ProductMapping("query", "CONTINENTE", "SKU-1", "Name");
		when(productMappingRepository.save(any(ProductMapping.class))).thenReturn(mapping);

		// Act
		productService.saveMapping("query", "CONTINENTE", result);

		// Assert
		verify(productRepository).findByExternalIdAndSupermarket("SKU-1", "CONTINENTE");
		verify(productMappingRepository).save(any(ProductMapping.class));
	}

	@Test
	void saveMappingWithPriority_newPriority_savesMappingWithFallbackId() {
		// Arrange
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

		// Act
		productService.saveMappingWithPriority("query", "CONTINENTE", result, 1);

		// Assert
		verify(productRepository).findByExternalIdAndSupermarket("SKU-Alt", "CONTINENTE");
		verify(productMappingRepository).save(any(ProductMapping.class));
	}

	@Test
	void saveMappingWithPriority_duplicateSku_skipsSave() {
		// Arrange
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

		// Act
		productService.saveMappingWithPriority("query", "CONTINENTE", result, 1);

		// Assert
		verify(productRepository).findByExternalIdAndSupermarket("SKU-1", "CONTINENTE");
		verify(productMappingRepository, never()).save(any(ProductMapping.class));
	}

	@Test
	void promoteMapping_secondaryMapping_swapsPriorityWithUpper() {
		// Arrange
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

		// Act
		productService.promoteMapping(11L);

		// Assert
		verify(productMappingRepository, atLeastOnce()).saveAndFlush(m1);
		verify(productMappingRepository, atLeastOnce()).saveAndFlush(m0);
	}

	@Test
	void demoteMapping_primaryMapping_swapsPriorityWithLower() {
		// Arrange
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

		// Act
		productService.demoteMapping(10L);

		// Assert
		verify(productMappingRepository, atLeastOnce()).saveAndFlush(m0);
		verify(productMappingRepository, atLeastOnce()).saveAndFlush(m1);
	}

	@Test
	void deleteMapping_nonexistentId_doesNothing() {
		// Arrange
		when(productMappingRepository.findById(99L)).thenReturn(Optional.empty());

		// Act
		productService.deleteMapping(99L);

		// Assert
		verify(productMappingRepository, never()).delete(any());
	}

	@Test
	void promoteMapping_nonexistentId_doesNothing() {
		// Arrange
		when(productMappingRepository.findById(99L)).thenReturn(Optional.empty());

		// Act
		productService.promoteMapping(99L);

		// Assert
		verify(productMappingRepository, never()).saveAndFlush(any());
	}

	@Test
	void promoteMapping_alreadyPrimary_doesNothing() {
		// Arrange
		ProductMapping m0 = new ProductMapping("query", "CONTINENTE", "SKU-0", "Product 0");
		m0.setId(10L);
		m0.setPriority(0);

		when(productMappingRepository.findById(10L)).thenReturn(Optional.of(m0));

		// Act
		productService.promoteMapping(10L);

		// Assert
		verify(productMappingRepository, never()).saveAndFlush(any());
	}

	@Test
	void promoteMapping_noAboveElement_doesNothing() {
		// Arrange
		ProductMapping m1 = new ProductMapping("query", "CONTINENTE", "SKU-1", "Product 1");
		m1.setId(11L);
		m1.setPriority(2);

		ProductMapping m0 = new ProductMapping("query", "CONTINENTE", "SKU-0", "Product 0");
		m0.setId(10L);
		m0.setPriority(0);

		when(productMappingRepository.findById(11L)).thenReturn(Optional.of(m1));
		when(productMappingRepository.findBySearchTextAndSupermarketOrderByPriorityAsc("query", "CONTINENTE"))
				.thenReturn(List.of(m0, m1));

		// Act
		productService.promoteMapping(11L);

		// Assert
		verify(productMappingRepository, never()).saveAndFlush(any());
	}

	@Test
	void demoteMapping_nonexistentId_doesNothing() {
		// Arrange
		when(productMappingRepository.findById(99L)).thenReturn(Optional.empty());

		// Act
		productService.demoteMapping(99L);

		// Assert
		verify(productMappingRepository, never()).saveAndFlush(any());
	}

	@Test
	void demoteMapping_alreadyAtBottom_doesNothing() {
		// Arrange
		ProductMapping m0 = new ProductMapping("query", "CONTINENTE", "SKU-0", "Product 0");
		m0.setId(10L);
		m0.setPriority(0);

		when(productMappingRepository.findById(10L)).thenReturn(Optional.of(m0));
		when(productMappingRepository.findBySearchTextAndSupermarketOrderByPriorityAsc("query", "CONTINENTE"))
				.thenReturn(List.of(m0));

		// Act
		productService.demoteMapping(10L);

		// Assert
		verify(productMappingRepository, never()).saveAndFlush(any());
	}

	@Test
	void demoteMapping_noBelowElement_doesNothing() {
		// Arrange
		ProductMapping m0 = new ProductMapping("query", "CONTINENTE", "SKU-0", "Product 0");
		m0.setId(10L);
		m0.setPriority(0);

		ProductMapping m2 = new ProductMapping("query", "CONTINENTE", "SKU-2", "Product 2");
		m2.setId(12L);
		m2.setPriority(2);

		when(productMappingRepository.findById(10L)).thenReturn(Optional.of(m0));
		when(productMappingRepository.findBySearchTextAndSupermarketOrderByPriorityAsc("query", "CONTINENTE"))
				.thenReturn(List.of(m0, m2));

		// Act
		productService.demoteMapping(10L);

		// Assert
		verify(productMappingRepository, never()).saveAndFlush(any());
	}
}
