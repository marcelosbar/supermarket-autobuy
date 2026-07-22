package com.autobuy.service;

import com.autobuy.driver.SupermarketDriver;
import com.autobuy.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductResolutionServiceTest {

	@Mock
	private ProductService productService;

	@Mock
	private PriceHistoryService priceHistoryService;

	@Mock
	private SupermarketDriver driver;

	private AutoBuyExecutionContext executionContext;
	private ProductResolutionService service;

	@BeforeEach
	void setUp() {
		executionContext = new AutoBuyExecutionContext();
		service = new ProductResolutionService(productService, priceHistoryService, executionContext);
	}

	@Test
	void partitionByMappingStatus_mixedItems_returnsUnmappedItemsFirst() {
		// Arrange
		ShoppingItem item1 = new ShoppingItem("apples", 1);
		ShoppingItem item2 = new ShoppingItem("bananas", 2);

		when(productService.findMappingsBySearchTextAndSupermarket("apples", "CONTINENTE"))
				.thenReturn(List.of(new ProductMapping("apples", "CONTINENTE", "sku1", "Apples")));
		when(productService.findMappingsBySearchTextAndSupermarket("bananas", "CONTINENTE")).thenReturn(List.of());

		// Act
		List<ShoppingItem> partitioned = service.partitionByMappingStatus(List.of(item1, item2), "CONTINENTE");

		// Assert
		// Unmapped (bananas) first, mapped (apples) second
		assertEquals(2, partitioned.size());
		assertEquals("bananas", partitioned.get(0).query());
		assertEquals("apples", partitioned.get(1).query());
	}

	@Test
	void resolveProduct_mappedItemAvailable_returnsResolveResult() throws InterruptedException {
		// Arrange
		ShoppingItem item = new ShoppingItem("apples", 1);
		ProductMapping mapping = new ProductMapping("apples", "CONTINENTE", "sku1", "Apples");
		SearchResult result = new SearchResult("sku1", "Apples", "Brand", BigDecimal.ONE, "url", "Fruit");

		when(productService.findMappingsBySearchTextAndSupermarket("apples", "CONTINENTE"))
				.thenReturn(List.of(mapping));
		when(driver.searchProduct("sku1")).thenReturn(List.of(result));
		when(driver.isProductAvailable("sku1")).thenReturn(true);

		// Act
		ResolveResult resolveResult = service.resolveProduct(driver, item, "CONTINENTE");

		// Assert
		assertNotNull(resolveResult);
		assertEquals(result, resolveResult.product());
		assertFalse(resolveResult.alreadyAdded());
	}

	@Test
	void resolveProduct_mappedItemUnavailable_defersToExhaustedItemsList() throws InterruptedException {
		// Arrange
		ShoppingItem item = new ShoppingItem("apples", 1);
		ProductMapping mapping = new ProductMapping("apples", "CONTINENTE", "sku1", "Apples");
		SearchResult result = new SearchResult("sku1", "Apples", "Brand", BigDecimal.ONE, "url", "Fruit");

		when(productService.findMappingsBySearchTextAndSupermarket("apples", "CONTINENTE"))
				.thenReturn(List.of(mapping));
		when(driver.searchProduct("sku1")).thenReturn(List.of(result));
		when(driver.isProductAvailable("sku1")).thenReturn(false);

		// Act
		ResolveResult resolveResult = service.resolveProduct(driver, item, "CONTINENTE");

		// Assert
		assertNull(resolveResult);
		assertEquals(1, executionContext.getExhaustedItems().size());
		assertEquals(item, executionContext.getExhaustedItems().get(0));
	}
}
