package com.autobuy.service;

import com.autobuy.driver.SupermarketDriver;
import com.autobuy.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductResolutionServiceTest {

	private ProductService productService;
	private PriceHistoryService priceHistoryService;
	private AutoBuyExecutionContext executionContext;
	private SupermarketDriver driver;
	private ProductResolutionService service;

	@BeforeEach
	void setUp() {
		productService = mock(ProductService.class);
		priceHistoryService = mock(PriceHistoryService.class);
		executionContext = new AutoBuyExecutionContext();
		driver = mock(SupermarketDriver.class);
		service = new ProductResolutionService(productService, priceHistoryService, executionContext);
	}

	@Test
	void testPartitionByMappingStatus() {
		ShoppingItem item1 = new ShoppingItem("apples", 1);
		ShoppingItem item2 = new ShoppingItem("bananas", 2);

		when(productService.findMappingsBySearchTextAndSupermarket("apples", "CONTINENTE"))
				.thenReturn(List.of(new ProductMapping("apples", "CONTINENTE", "sku1", "Apples")));
		when(productService.findMappingsBySearchTextAndSupermarket("bananas", "CONTINENTE")).thenReturn(List.of());

		List<ShoppingItem> partitioned = service.partitionByMappingStatus(List.of(item1, item2), "CONTINENTE");

		// Unmapped (bananas) first, mapped (apples) second
		assertEquals(2, partitioned.size());
		assertEquals("bananas", partitioned.get(0).query());
		assertEquals("apples", partitioned.get(1).query());
	}

	@Test
	void testResolveProduct_WithMappingsAvailable() throws InterruptedException {
		ShoppingItem item = new ShoppingItem("apples", 1);
		ProductMapping mapping = new ProductMapping("apples", "CONTINENTE", "sku1", "Apples");
		SearchResult result = new SearchResult("sku1", "Apples", "Brand", BigDecimal.ONE, "url", "Fruit");

		when(productService.findMappingsBySearchTextAndSupermarket("apples", "CONTINENTE"))
				.thenReturn(List.of(mapping));
		when(driver.searchProduct("sku1")).thenReturn(List.of(result));
		when(driver.isProductAvailable("sku1")).thenReturn(true);

		ResolveResult resolveResult = service.resolveProduct(driver, item, "CONTINENTE");

		assertNotNull(resolveResult);
		assertEquals(result, resolveResult.product());
		assertFalse(resolveResult.alreadyAdded());
	}

	@Test
	void testResolveProduct_WithMappingsUnavailableDefers() throws InterruptedException {
		ShoppingItem item = new ShoppingItem("apples", 1);
		ProductMapping mapping = new ProductMapping("apples", "CONTINENTE", "sku1", "Apples");
		SearchResult result = new SearchResult("sku1", "Apples", "Brand", BigDecimal.ONE, "url", "Fruit");

		when(productService.findMappingsBySearchTextAndSupermarket("apples", "CONTINENTE"))
				.thenReturn(List.of(mapping));
		when(driver.searchProduct("sku1")).thenReturn(List.of(result));
		when(driver.isProductAvailable("sku1")).thenReturn(false);

		ResolveResult resolveResult = service.resolveProduct(driver, item, "CONTINENTE");

		assertNull(resolveResult);
		assertEquals(1, executionContext.getExhaustedItems().size());
		assertEquals(item, executionContext.getExhaustedItems().get(0));
	}
}
