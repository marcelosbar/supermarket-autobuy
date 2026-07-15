package com.autobuy.service;

import com.autobuy.config.MemoryAppender;
import com.autobuy.driver.SupermarketDriver;
import com.autobuy.model.*;
import com.autobuy.provider.CredentialProvider;
import com.autobuy.provider.ShoppingListProvider;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AutoBuyOrchestrationServiceTest {

	private ProductService productService;
	private PriceHistoryService priceHistoryService;
	private SupermarketDriver supermarketDriver;
	private CredentialProvider credentialProvider;
	private ShoppingListProvider shoppingListProvider;
	private ThreadPoolTaskExecutor taskExecutor;

	private AutoBuyExecutionContext executionContext;
	private ProductResolutionService productResolutionService;
	private ExecutionProviders executionProviders;
	private AutoBuyOrchestrationService service;

	@BeforeEach
	void setUp() {
		MemoryAppender.clear();
		productService = mock(ProductService.class);
		priceHistoryService = mock(PriceHistoryService.class);
		supermarketDriver = mock(SupermarketDriver.class);
		credentialProvider = mock(CredentialProvider.class);
		shoppingListProvider = mock(ShoppingListProvider.class);

		taskExecutor = new ThreadPoolTaskExecutor();
		taskExecutor.setCorePoolSize(2);
		taskExecutor.setMaxPoolSize(2);
		taskExecutor.setQueueCapacity(10);
		taskExecutor.setThreadNamePrefix("Test-AutoBuy-");
		taskExecutor.initialize();

		when(supermarketDriver.getSupermarketName()).thenReturn("CONTINENTE");
		when(supermarketDriver.isProductAvailable(anyString())).thenReturn(true);

		executionContext = new AutoBuyExecutionContext();
		productResolutionService = spy(
				new ProductResolutionService(productService, priceHistoryService, executionContext));
		executionProviders = new ExecutionProviders(credentialProvider, shoppingListProvider);

		service = new AutoBuyOrchestrationService(List.of(supermarketDriver), executionContext,
				productResolutionService, taskExecutor, executionProviders);
	}

	@AfterEach
	void tearDown() {
		if (service != null) {
			try {
				service.cancel();
			} catch (Exception _) {
				// ignore
			}
		}
		taskExecutor.shutdown();
	}

	private void awaitState(AutoBuyState targetState) {
		Awaitility.await().atMost(10, TimeUnit.SECONDS)
				.until(() -> executionContext.getStatus().state() == targetState);
	}

	@Test
	void testStartAutoBuy_DriverNotFound() {
		service.startAutoBuy("list.json", "NONEXISTENT", false);
		awaitState(AutoBuyState.FAILED);
		var status = executionContext.getStatus();
		assertTrue(status.error().contains("No driver found"));
	}

	@Test
	void testStartAutoBuy_EmptyShoppingList() {
		when(shoppingListProvider.getShoppingList("list.json")).thenReturn(List.of());

		service.startAutoBuy("list.json", "CONTINENTE", false);
		awaitState(AutoBuyState.FAILED);
		var status = executionContext.getStatus();
		assertTrue(status.error().contains("Shopping list is empty"));
	}

	@Test
	void testStartAutoBuy_MissingCredentials() {
		when(shoppingListProvider.getShoppingList("list.json")).thenReturn(List.of(new ShoppingItem("apples", 1)));
		when(credentialProvider.getUsername("CONTINENTE")).thenReturn("");
		when(credentialProvider.getPassword("CONTINENTE")).thenReturn("");

		service.startAutoBuy("list.json", "CONTINENTE", false);
		awaitState(AutoBuyState.FAILED);
		var status = executionContext.getStatus();
		assertTrue(status.error().contains("Credentials for CONTINENTE are not configured"));
	}

	@Test
	void testStartAutoBuy_DriverInitFailure() {
		when(shoppingListProvider.getShoppingList("list.json")).thenReturn(List.of(new ShoppingItem("apples", 1)));
		when(credentialProvider.getUsername("CONTINENTE")).thenReturn("user");
		when(credentialProvider.getPassword("CONTINENTE")).thenReturn("pass");
		doThrow(new RuntimeException("Playwright crashed")).when(supermarketDriver).initialize(anyString(), anyString(),
				anyBoolean());

		service.startAutoBuy("list.json", "CONTINENTE", false);
		awaitState(AutoBuyState.FAILED);
		var status = executionContext.getStatus();
		assertTrue(status.error().contains("Failed to initialize driver"));
		verify(supermarketDriver, timeout(2000).atLeastOnce()).close();
	}

	@Test
	void testStartAutoBuy_HappyPathWithMappings() {
		ShoppingItem item = new ShoppingItem("apples", 2);
		ProductMapping mapping = new ProductMapping("apples", "CONTINENTE", "sku123", "Red Apples");
		SearchResult searchResult = new SearchResult("sku123", "Red Apples", "BrandA", BigDecimal.valueOf(1.99), "url",
				"Fruit");

		when(shoppingListProvider.getShoppingList("list.json")).thenReturn(List.of(item));
		when(credentialProvider.getUsername("CONTINENTE")).thenReturn("user");
		when(credentialProvider.getPassword("CONTINENTE")).thenReturn("pass");
		when(productService.findMappingsBySearchTextAndSupermarket("apples", "CONTINENTE"))
				.thenReturn(List.of(mapping));
		when(supermarketDriver.searchProduct("sku123")).thenReturn(List.of(searchResult));
		when(supermarketDriver.isProductAvailable("sku123")).thenReturn(true);
		when(supermarketDriver.addProductToCart("sku123", 2)).thenReturn(true);

		service.startAutoBuy("list.json", "CONTINENTE", false);

		awaitState(AutoBuyState.AWAITING_FINAL_REVIEW);

		var status = executionContext.getStatus();
		assertEquals("apples", status.currentItemQuery());
		assertEquals(2, status.currentItemQuantity());

		service.completeRun();

		awaitState(AutoBuyState.SUCCESS);

		verify(priceHistoryService).logPrice(searchResult, "CONTINENTE");
		verify(supermarketDriver).navigateToCart();
		verify(supermarketDriver, timeout(2000).atLeastOnce()).close();
	}

	@Test
	void testStartAutoBuy_InteractiveResolution() {
		ShoppingItem item = new ShoppingItem("apples", 2);
		SearchResult searchResult1 = new SearchResult("skuA", "Green Apples", "Brand", BigDecimal.valueOf(1.5), "url",
				"Fruit");
		SearchResult searchResult2 = new SearchResult("skuB", "Red Apples", "Brand", BigDecimal.valueOf(1.9), "url",
				"Fruit");

		when(shoppingListProvider.getShoppingList("list.json")).thenReturn(List.of(item));
		when(credentialProvider.getUsername("CONTINENTE")).thenReturn("user");
		when(credentialProvider.getPassword("CONTINENTE")).thenReturn("pass");
		when(productService.findMappingsBySearchTextAndSupermarket("apples", "CONTINENTE")).thenReturn(List.of());
		when(supermarketDriver.searchProduct("apples"))
				.thenReturn(new ArrayList<>(List.of(searchResult1, searchResult2)));
		when(supermarketDriver.addProductToCart("skuB", 2)).thenReturn(true);

		service.startAutoBuy("list.json", "CONTINENTE", false);

		awaitState(AutoBuyState.AWAITING_MAPPING);

		productResolutionService.resolveMapping("skuB", true);

		awaitState(AutoBuyState.AWAITING_FINAL_REVIEW);

		verify(productService).saveMappingWithPriority("apples", "CONTINENTE", searchResult2, 0);
		verify(supermarketDriver).addProductToCart("skuB", 2);

		service.completeRun();
		awaitState(AutoBuyState.SUCCESS);
		verify(supermarketDriver, timeout(2000).atLeastOnce()).close();
	}

	@Test
	void testStartAutoBuy_InteractiveResolutionSkip() {
		ShoppingItem item = new ShoppingItem("apples", 2);
		SearchResult searchResult1 = new SearchResult("skuA", "Green Apples", "Brand", BigDecimal.valueOf(1.5), "url",
				"Fruit");

		when(shoppingListProvider.getShoppingList("list.json")).thenReturn(List.of(item));
		when(credentialProvider.getUsername("CONTINENTE")).thenReturn("user");
		when(credentialProvider.getPassword("CONTINENTE")).thenReturn("pass");
		when(productService.findMappingsBySearchTextAndSupermarket("apples", "CONTINENTE")).thenReturn(List.of());
		when(supermarketDriver.searchProduct("apples")).thenReturn(new ArrayList<>(List.of(searchResult1)));

		service.startAutoBuy("list.json", "CONTINENTE", false);

		awaitState(AutoBuyState.AWAITING_MAPPING);

		productResolutionService.resolveMapping("skip", false);

		awaitState(AutoBuyState.AWAITING_FINAL_REVIEW);

		verify(productService, never()).saveMappingWithPriority(anyString(), anyString(), any(), anyInt());
		verify(supermarketDriver, never()).addProductToCart(anyString(), anyInt());

		service.completeRun();
		awaitState(AutoBuyState.SUCCESS);
		verify(supermarketDriver, timeout(2000).atLeastOnce()).close();
	}

	@Test
	void testResolveMapping_StateTransitionsToRunning() {
		ShoppingItem item = new ShoppingItem("apples", 2);
		SearchResult searchResult = new SearchResult("skuB", "Red Apples", "Brand", BigDecimal.valueOf(1.9), "url",
				"Fruit");

		when(shoppingListProvider.getShoppingList("list.json")).thenReturn(List.of(item));
		when(credentialProvider.getUsername("CONTINENTE")).thenReturn("user");
		when(credentialProvider.getPassword("CONTINENTE")).thenReturn("pass");
		when(productService.findMappingsBySearchTextAndSupermarket("apples", "CONTINENTE")).thenReturn(List.of());
		when(supermarketDriver.searchProduct("apples")).thenReturn(new ArrayList<>(List.of(searchResult)));

		when(supermarketDriver.addProductToCart("skuB", 2)).thenReturn(true);

		service.startAutoBuy("list.json", "CONTINENTE", false);
		awaitState(AutoBuyState.AWAITING_MAPPING);

		var status = productResolutionService.resolveMapping("skuB", true);

		assertTrue(status.added());
		AutoBuyState currentState = executionContext.getStatus().state();
		assertTrue(currentState == AutoBuyState.RUNNING || currentState == AutoBuyState.AWAITING_FINAL_REVIEW);

		awaitState(AutoBuyState.AWAITING_FINAL_REVIEW);
		service.completeRun();
	}

	@Test
	void testCancelRun() {
		ShoppingItem item = new ShoppingItem("apples", 2);
		when(shoppingListProvider.getShoppingList("list.json")).thenReturn(List.of(item));
		when(credentialProvider.getUsername("CONTINENTE")).thenReturn("user");
		when(credentialProvider.getPassword("CONTINENTE")).thenReturn("pass");
		when(productService.findMappingsBySearchTextAndSupermarket("apples", "CONTINENTE")).thenReturn(List.of());
		when(supermarketDriver.searchProduct("apples")).thenReturn(
				new ArrayList<>(List.of(new SearchResult("skuA", "Name", "Brand", BigDecimal.ONE, "url", "cat"))));

		service.startAutoBuy("list.json", "CONTINENTE", false);

		awaitState(AutoBuyState.AWAITING_MAPPING);

		service.cancel();

		awaitState(AutoBuyState.FAILED);
		var status = executionContext.getStatus();
		assertTrue(status.error().contains("canceled by user"));
		verify(supermarketDriver, timeout(2000).atLeastOnce()).close();
	}

	@Test
	void testIllegalStateTransitions() {
		assertThrows(IllegalStateException.class, () -> productResolutionService.resolveMapping("sku123", true));
		assertThrows(IllegalStateException.class, () -> service.completeRun());
		assertThrows(IllegalStateException.class, () -> productResolutionService.refineSearch("query"));

		ShoppingItem item = new ShoppingItem("apples", 2);
		when(shoppingListProvider.getShoppingList("list.json")).thenReturn(List.of(item));
		when(credentialProvider.getUsername("CONTINENTE")).thenReturn("user");
		when(credentialProvider.getPassword("CONTINENTE")).thenReturn("pass");
		when(productService.findMappingsBySearchTextAndSupermarket("apples", "CONTINENTE")).thenReturn(List.of());
		when(supermarketDriver.searchProduct("apples")).thenReturn(List.of());

		service.startAutoBuy("list.json", "CONTINENTE", false);

		assertThrows(IllegalStateException.class, () -> service.startAutoBuy("list.json", "CONTINENTE", false));
	}

	@Test
	void testProcessingOrder_UnmappedItemsFirst() {
		ShoppingItem itemA = new ShoppingItem("apples", 1);
		ShoppingItem itemB = new ShoppingItem("bananas", 2);
		ShoppingItem itemC = new ShoppingItem("carrots", 3);

		ProductMapping mappingA = new ProductMapping("apples", "CONTINENTE", "skuA", "Apples");
		ProductMapping mappingC = new ProductMapping("carrots", "CONTINENTE", "skuC", "Carrots");

		SearchResult resA = new SearchResult("skuA", "Apples", "Brand", BigDecimal.ONE, "url", "Fruit");
		SearchResult resB = new SearchResult("skuB", "Bananas", "Brand", BigDecimal.ONE, "url", "Fruit");
		SearchResult resC = new SearchResult("skuC", "Carrots", "Brand", BigDecimal.ONE, "url", "Fruit");

		when(shoppingListProvider.getShoppingList("list.json")).thenReturn(List.of(itemA, itemB, itemC));
		when(credentialProvider.getUsername("CONTINENTE")).thenReturn("user");
		when(credentialProvider.getPassword("CONTINENTE")).thenReturn("pass");

		when(productService.findMappingsBySearchTextAndSupermarket("apples", "CONTINENTE"))
				.thenReturn(List.of(mappingA));
		when(productService.findMappingsBySearchTextAndSupermarket("bananas", "CONTINENTE")).thenReturn(List.of());
		when(productService.findMappingsBySearchTextAndSupermarket("carrots", "CONTINENTE"))
				.thenReturn(List.of(mappingC));

		when(supermarketDriver.searchProduct("bananas")).thenReturn(new ArrayList<>(List.of(resB)));
		when(supermarketDriver.searchProduct("skuA")).thenReturn(List.of(resA));
		when(supermarketDriver.isProductAvailable("skuA")).thenReturn(true);
		when(supermarketDriver.searchProduct("skuC")).thenReturn(List.of(resC));
		when(supermarketDriver.isProductAvailable("skuC")).thenReturn(true);

		when(supermarketDriver.addProductToCart(anyString(), anyInt())).thenReturn(true);

		service.startAutoBuy("list.json", "CONTINENTE", false);

		awaitState(AutoBuyState.AWAITING_MAPPING);
		assertEquals("bananas", executionContext.getStatus().currentItemQuery());

		productResolutionService.resolveMapping("skuB", true);

		awaitState(AutoBuyState.AWAITING_FINAL_REVIEW);

		service.completeRun();
		awaitState(AutoBuyState.SUCCESS);

		InOrder inOrder = inOrder(supermarketDriver);
		inOrder.verify(supermarketDriver).searchProduct("bananas");
		inOrder.verify(supermarketDriver).addProductToCart("skuB", 2);
		inOrder.verify(supermarketDriver).searchProduct("skuA");
		inOrder.verify(supermarketDriver).addProductToCart("skuA", 1);
		inOrder.verify(supermarketDriver).searchProduct("skuC");
		inOrder.verify(supermarketDriver).addProductToCart("skuC", 3);
		inOrder.verify(supermarketDriver).navigateToCart();
	}

	@Test
	void testProcessingOrder_AllMapped() {
		ShoppingItem itemA = new ShoppingItem("apples", 1);
		ShoppingItem itemC = new ShoppingItem("carrots", 3);

		ProductMapping mappingA = new ProductMapping("apples", "CONTINENTE", "skuA", "Apples");
		ProductMapping mappingC = new ProductMapping("carrots", "CONTINENTE", "skuC", "Carrots");

		SearchResult resA = new SearchResult("skuA", "Apples", "Brand", BigDecimal.ONE, "url", "Fruit");
		SearchResult resC = new SearchResult("skuC", "Carrots", "Brand", BigDecimal.ONE, "url", "Fruit");

		when(shoppingListProvider.getShoppingList("list.json")).thenReturn(List.of(itemA, itemC));
		when(credentialProvider.getUsername("CONTINENTE")).thenReturn("user");
		when(credentialProvider.getPassword("CONTINENTE")).thenReturn("pass");

		when(productService.findMappingsBySearchTextAndSupermarket("apples", "CONTINENTE"))
				.thenReturn(List.of(mappingA));
		when(productService.findMappingsBySearchTextAndSupermarket("carrots", "CONTINENTE"))
				.thenReturn(List.of(mappingC));

		when(supermarketDriver.searchProduct("skuA")).thenReturn(List.of(resA));
		when(supermarketDriver.isProductAvailable("skuA")).thenReturn(true);
		when(supermarketDriver.searchProduct("skuC")).thenReturn(List.of(resC));
		when(supermarketDriver.isProductAvailable("skuC")).thenReturn(true);
		when(supermarketDriver.addProductToCart(anyString(), anyInt())).thenReturn(true);

		service.startAutoBuy("list.json", "CONTINENTE", false);

		awaitState(AutoBuyState.AWAITING_FINAL_REVIEW);

		service.completeRun();
		awaitState(AutoBuyState.SUCCESS);

		InOrder inOrder = inOrder(supermarketDriver);
		inOrder.verify(supermarketDriver).searchProduct("skuA");
		inOrder.verify(supermarketDriver).addProductToCart("skuA", 1);
		inOrder.verify(supermarketDriver).searchProduct("skuC");
		inOrder.verify(supermarketDriver).addProductToCart("skuC", 3);
		inOrder.verify(supermarketDriver).navigateToCart();
	}

	@Test
	void testStartAutoBuy_MappedSkuNotFound() {
		ShoppingItem item = new ShoppingItem("apples", 2);
		ProductMapping mapping = new ProductMapping("apples", "CONTINENTE", "sku123", "Red Apples");

		when(shoppingListProvider.getShoppingList("list.json")).thenReturn(List.of(item));
		when(credentialProvider.getUsername("CONTINENTE")).thenReturn("user");
		when(credentialProvider.getPassword("CONTINENTE")).thenReturn("pass");
		when(productService.findMappingsBySearchTextAndSupermarket("apples", "CONTINENTE"))
				.thenReturn(List.of(mapping));
		when(supermarketDriver.searchProduct("sku123")).thenReturn(List.of());
		when(supermarketDriver.searchProduct("apples")).thenReturn(List.of());

		service.startAutoBuy("list.json", "CONTINENTE", false);

		awaitState(AutoBuyState.AWAITING_EXHAUSTED_RESOLUTIONS);

		productResolutionService.resolveMapping("skip", false);

		awaitState(AutoBuyState.AWAITING_FINAL_REVIEW);

		var status = executionContext.getStatus();
		assertEquals(1, status.skippedItems().size());
		assertEquals("apples", status.skippedItems().get(0));

		service.completeRun();
		awaitState(AutoBuyState.SUCCESS);
	}

	@Test
	void testStartAutoBuy_MappedSkuUnavailable() {
		ShoppingItem item = new ShoppingItem("apples", 2);
		ProductMapping mapping = new ProductMapping("apples", "CONTINENTE", "sku123", "Red Apples");
		SearchResult searchResult = new SearchResult("sku123", "Red Apples", "BrandA", BigDecimal.valueOf(1.99), "url",
				"Fruit");

		when(shoppingListProvider.getShoppingList("list.json")).thenReturn(List.of(item));
		when(credentialProvider.getUsername("CONTINENTE")).thenReturn("user");
		when(credentialProvider.getPassword("CONTINENTE")).thenReturn("pass");
		when(productService.findMappingsBySearchTextAndSupermarket("apples", "CONTINENTE"))
				.thenReturn(List.of(mapping));
		when(supermarketDriver.searchProduct("sku123")).thenReturn(List.of(searchResult));
		when(supermarketDriver.isProductAvailable("sku123")).thenReturn(false);
		when(supermarketDriver.searchProduct("apples")).thenReturn(List.of());

		service.startAutoBuy("list.json", "CONTINENTE", false);

		awaitState(AutoBuyState.AWAITING_EXHAUSTED_RESOLUTIONS);

		productResolutionService.resolveMapping("skip", false);

		awaitState(AutoBuyState.AWAITING_FINAL_REVIEW);

		var status = executionContext.getStatus();
		assertEquals(1, status.skippedItems().size());
		assertEquals("apples", status.skippedItems().get(0));

		service.completeRun();
		awaitState(AutoBuyState.SUCCESS);
	}

	@Test
	void testStartAutoBuy_CartAddFailure() {
		ShoppingItem item = new ShoppingItem("apples", 2);
		ProductMapping mapping = new ProductMapping("apples", "CONTINENTE", "sku123", "Red Apples");
		SearchResult searchResult = new SearchResult("sku123", "Red Apples", "BrandA", BigDecimal.valueOf(1.99), "url",
				"Fruit");

		when(shoppingListProvider.getShoppingList("list.json")).thenReturn(List.of(item));
		when(credentialProvider.getUsername("CONTINENTE")).thenReturn("user");
		when(credentialProvider.getPassword("CONTINENTE")).thenReturn("pass");
		when(productService.findMappingsBySearchTextAndSupermarket("apples", "CONTINENTE"))
				.thenReturn(List.of(mapping));
		when(supermarketDriver.searchProduct("sku123")).thenReturn(List.of(searchResult));
		when(supermarketDriver.isProductAvailable("sku123")).thenReturn(true);
		when(supermarketDriver.addProductToCart("sku123", 2)).thenReturn(false);
		when(supermarketDriver.searchProduct("apples")).thenReturn(List.of());

		service.startAutoBuy("list.json", "CONTINENTE", false);

		awaitState(AutoBuyState.AWAITING_EXHAUSTED_RESOLUTIONS);

		productResolutionService.resolveMapping("skip", false);

		awaitState(AutoBuyState.AWAITING_FINAL_REVIEW);

		var status = executionContext.getStatus();
		assertEquals(1, status.skippedItems().size());
		assertEquals("apples", status.skippedItems().get(0));

		service.completeRun();
		awaitState(AutoBuyState.SUCCESS);
	}

	@Test
	void testStartAutoBuy_NavigateToCartFailure() {
		ShoppingItem item = new ShoppingItem("apples", 2);
		ProductMapping mapping = new ProductMapping("apples", "CONTINENTE", "sku123", "Red Apples");
		SearchResult searchResult = new SearchResult("sku123", "Red Apples", "BrandA", BigDecimal.valueOf(1.99), "url",
				"Fruit");

		when(shoppingListProvider.getShoppingList("list.json")).thenReturn(List.of(item));
		when(credentialProvider.getUsername("CONTINENTE")).thenReturn("user");
		when(credentialProvider.getPassword("CONTINENTE")).thenReturn("pass");
		when(productService.findMappingsBySearchTextAndSupermarket("apples", "CONTINENTE"))
				.thenReturn(List.of(mapping));
		when(supermarketDriver.searchProduct("sku123")).thenReturn(List.of(searchResult));
		when(supermarketDriver.addProductToCart("sku123", 2)).thenReturn(true);

		doThrow(new RuntimeException("Navigation failed")).when(supermarketDriver).navigateToCart();

		service.startAutoBuy("list.json", "CONTINENTE", false);

		awaitState(AutoBuyState.AWAITING_FINAL_REVIEW);

		service.completeRun();

		awaitState(AutoBuyState.SUCCESS);

		verify(supermarketDriver).navigateToCart();
		verify(supermarketDriver, timeout(2000).atLeastOnce()).close();
	}

	@Test
	void testStartAutoBuy_InteractiveResolutionRefine() {
		ShoppingItem item = new ShoppingItem("apples", 2);
		SearchResult initialResult = new SearchResult("skuA", "Green Apples", "Brand", BigDecimal.valueOf(1.5), "url",
				"Fruit");
		SearchResult refinedResult = new SearchResult("skuB", "Red Apples", "Brand", BigDecimal.valueOf(1.9), "url",
				"Fruit");

		when(shoppingListProvider.getShoppingList("list.json")).thenReturn(List.of(item));
		when(credentialProvider.getUsername("CONTINENTE")).thenReturn("user");
		when(credentialProvider.getPassword("CONTINENTE")).thenReturn("pass");
		when(productService.findMappingsBySearchTextAndSupermarket("apples", "CONTINENTE")).thenReturn(List.of());

		when(supermarketDriver.searchProduct("apples")).thenReturn(new ArrayList<>(List.of(initialResult)));
		when(supermarketDriver.searchProduct("red apples")).thenReturn(new ArrayList<>(List.of(refinedResult)));
		when(supermarketDriver.addProductToCart("skuB", 2)).thenReturn(true);

		service.startAutoBuy("list.json", "CONTINENTE", false);

		awaitState(AutoBuyState.AWAITING_MAPPING);

		var status = executionContext.getStatus();
		assertEquals(1, status.searchResults().size());
		assertEquals("skuA", status.searchResults().get(0).externalId());

		productResolutionService.refineSearch("red apples");

		awaitState(AutoBuyState.AWAITING_MAPPING);

		status = executionContext.getStatus();
		assertEquals(1, status.searchResults().size());
		assertEquals("skuB", status.searchResults().get(0).externalId());

		productResolutionService.resolveMapping("skuB", true);

		awaitState(AutoBuyState.AWAITING_FINAL_REVIEW);

		verify(productService).saveMappingWithPriority("apples", "CONTINENTE", refinedResult, 0);

		service.completeRun();
		awaitState(AutoBuyState.SUCCESS);
	}

	@Test
	void testStartAutoBuy_InteractiveResolutionSaveMappingException() {
		ShoppingItem item = new ShoppingItem("apples", 2);
		SearchResult searchResult = new SearchResult("sku123", "Red Apples", "Brand", BigDecimal.valueOf(1.99), "url",
				"Fruit");

		when(shoppingListProvider.getShoppingList("list.json")).thenReturn(List.of(item));
		when(credentialProvider.getUsername("CONTINENTE")).thenReturn("user");
		when(credentialProvider.getPassword("CONTINENTE")).thenReturn("pass");
		when(productService.findMappingsBySearchTextAndSupermarket("apples", "CONTINENTE")).thenReturn(List.of());
		when(supermarketDriver.searchProduct("apples")).thenReturn(new ArrayList<>(List.of(searchResult)));
		when(supermarketDriver.addProductToCart("sku123", 2)).thenReturn(true);

		doThrow(new RuntimeException("Database error")).when(productService).saveMappingWithPriority("apples",
				"CONTINENTE", searchResult, 0);

		service.startAutoBuy("list.json", "CONTINENTE", false);

		awaitState(AutoBuyState.AWAITING_MAPPING);

		productResolutionService.resolveMapping("sku123", true);

		awaitState(AutoBuyState.AWAITING_FINAL_REVIEW);

		service.completeRun();
		awaitState(AutoBuyState.SUCCESS);
	}

	@Test
	void testStartAutoBuy_InteractiveResolutionExhaustedSelect() {
		ShoppingItem item = new ShoppingItem("apples", 2);
		ProductMapping mapping = new ProductMapping("apples", "CONTINENTE", "sku123", "Red Apples");
		SearchResult searchResult = new SearchResult("sku123", "Red Apples", "BrandA", BigDecimal.valueOf(1.99), "url",
				"Fruit");
		SearchResult alternativeResult = new SearchResult("sku456", "Green Apples", "BrandB", BigDecimal.valueOf(1.49),
				"url", "Fruit");

		when(shoppingListProvider.getShoppingList("list.json")).thenReturn(List.of(item));
		when(credentialProvider.getUsername("CONTINENTE")).thenReturn("user");
		when(credentialProvider.getPassword("CONTINENTE")).thenReturn("pass");
		when(productService.findMappingsBySearchTextAndSupermarket("apples", "CONTINENTE"))
				.thenReturn(List.of(mapping));
		when(supermarketDriver.searchProduct("sku123")).thenReturn(List.of(searchResult));
		when(supermarketDriver.isProductAvailable("sku123")).thenReturn(false);

		when(supermarketDriver.searchProduct("apples")).thenReturn(new ArrayList<>(List.of(alternativeResult)));
		when(supermarketDriver.isProductAvailable("sku456")).thenReturn(true);
		when(supermarketDriver.addProductToCart("sku456", 2)).thenReturn(true);

		service.startAutoBuy("list.json", "CONTINENTE", false);

		awaitState(AutoBuyState.AWAITING_EXHAUSTED_RESOLUTIONS);

		var status = productResolutionService.resolveMapping("sku456", true);
		assertTrue(status.added());

		awaitState(AutoBuyState.AWAITING_FINAL_REVIEW);

		service.completeRun();
		awaitState(AutoBuyState.SUCCESS);
	}

	@Test
	void testStartAutoBuy_InteractiveResolutionSelect_CartAddFailure() {
		ShoppingItem item = new ShoppingItem("apples", 2);
		SearchResult searchResult = new SearchResult("sku123", "Red Apples", "Brand", BigDecimal.valueOf(1.99), "url",
				"Fruit");

		when(shoppingListProvider.getShoppingList("list.json")).thenReturn(List.of(item));
		when(credentialProvider.getUsername("CONTINENTE")).thenReturn("user");
		when(credentialProvider.getPassword("CONTINENTE")).thenReturn("pass");
		when(productService.findMappingsBySearchTextAndSupermarket("apples", "CONTINENTE")).thenReturn(List.of());
		when(supermarketDriver.searchProduct("apples")).thenReturn(new ArrayList<>(List.of(searchResult)));
		when(supermarketDriver.addProductToCart("sku123", 2)).thenReturn(false);

		service.startAutoBuy("list.json", "CONTINENTE", false);

		awaitState(AutoBuyState.AWAITING_MAPPING);

		assertThrows(com.autobuy.exception.AutoBuyException.class,
				() -> productResolutionService.resolveMapping("sku123", false));
	}

	@Test
	void testStartAutoBuy_InteractiveResolutionSelect_CartAddFailureSaveMapping() {
		ShoppingItem item = new ShoppingItem("apples", 2);
		SearchResult searchResult = new SearchResult("sku123", "Red Apples", "Brand", BigDecimal.valueOf(1.99), "url",
				"Fruit");

		when(shoppingListProvider.getShoppingList("list.json")).thenReturn(List.of(item));
		when(credentialProvider.getUsername("CONTINENTE")).thenReturn("user");
		when(credentialProvider.getPassword("CONTINENTE")).thenReturn("pass");
		when(productService.findMappingsBySearchTextAndSupermarket("apples", "CONTINENTE")).thenReturn(List.of());
		when(supermarketDriver.searchProduct("apples")).thenReturn(new ArrayList<>(List.of(searchResult)));
		when(supermarketDriver.addProductToCart("sku123", 2)).thenReturn(false);

		service.startAutoBuy("list.json", "CONTINENTE", false);

		awaitState(AutoBuyState.AWAITING_MAPPING);

		var status = productResolutionService.resolveMapping("sku123", true);
		assertFalse(status.added());
		assertEquals("Saved as mapping, but out of stock. Please select a fallback alternative.", status.message());
	}

	@Test
	void testStartAutoBuy_InteractiveResolutionSelect_ProductNotFoundInSearchResults() {
		ShoppingItem item = new ShoppingItem("apples", 2);
		SearchResult searchResult = new SearchResult("sku123", "Red Apples", "Brand", BigDecimal.valueOf(1.99), "url",
				"Fruit");

		when(shoppingListProvider.getShoppingList("list.json")).thenReturn(List.of(item));
		when(credentialProvider.getUsername("CONTINENTE")).thenReturn("user");
		when(credentialProvider.getPassword("CONTINENTE")).thenReturn("pass");
		when(productService.findMappingsBySearchTextAndSupermarket("apples", "CONTINENTE")).thenReturn(List.of());
		when(supermarketDriver.searchProduct("apples")).thenReturn(new ArrayList<>(List.of(searchResult)));

		service.startAutoBuy("list.json", "CONTINENTE", false);

		awaitState(AutoBuyState.AWAITING_MAPPING);

		assertThrows(IllegalArgumentException.class,
				() -> productResolutionService.resolveMapping("nonexistent-sku", false));
	}

	@Test
	void testStartAutoBuy_ExhaustedResolution_AddCartFailureAndSaveMappingFalse() {
		ShoppingItem item = new ShoppingItem("apples", 2);
		ProductMapping mapping = new ProductMapping("apples", "CONTINENTE", "sku123", "Red Apples");
		SearchResult searchResult = new SearchResult("sku123", "Red Apples", "BrandA", BigDecimal.valueOf(1.99), "url",
				"Fruit");
		SearchResult alternativeResult = new SearchResult("sku456", "Green Apples", "BrandB", BigDecimal.valueOf(2.1),
				"url", "Fruit");

		when(shoppingListProvider.getShoppingList("list.json")).thenReturn(List.of(item));
		when(credentialProvider.getUsername("CONTINENTE")).thenReturn("user");
		when(credentialProvider.getPassword("CONTINENTE")).thenReturn("pass");
		when(productService.findMappingsBySearchTextAndSupermarket("apples", "CONTINENTE"))
				.thenReturn(List.of(mapping));
		when(supermarketDriver.searchProduct("sku123")).thenReturn(List.of(searchResult));
		when(supermarketDriver.isProductAvailable("sku123")).thenReturn(false);

		when(supermarketDriver.searchProduct("apples")).thenReturn(new ArrayList<>(List.of(alternativeResult)));
		when(supermarketDriver.addProductToCart("sku456", 2)).thenReturn(false);

		service.startAutoBuy("list.json", "CONTINENTE", false);
		awaitState(AutoBuyState.AWAITING_EXHAUSTED_RESOLUTIONS);

		productResolutionService.resolveMapping("sku456", false);

		awaitState(AutoBuyState.AWAITING_FINAL_REVIEW);

		var status = executionContext.getStatus();
		assertEquals(1, status.skippedItems().size());
		assertEquals("apples", status.skippedItems().get(0));

		service.completeRun();
		awaitState(AutoBuyState.SUCCESS);
	}

	@Test
	void testShutdown_WithActiveDriver() {
		executionContext.setActiveDriver(supermarketDriver);
		service.shutdown();
		verify(supermarketDriver).close();
		assertNull(executionContext.getActiveDriver());
	}

	@Test
	void testShutdown_WithActiveDriverCloseException() {
		doThrow(new RuntimeException("close failed")).when(supermarketDriver).close();
		executionContext.setActiveDriver(supermarketDriver);
		service.shutdown();
		verify(supermarketDriver).close();
		assertNull(executionContext.getActiveDriver());
	}

	@Test
	void testCompleteRun_KeepBrowser() {
		ShoppingItem item = new ShoppingItem("apples", 2);
		SearchResult searchResult = new SearchResult("sku123", "Red Apples", "Brand", BigDecimal.valueOf(1.99), "url",
				"Fruit");

		when(shoppingListProvider.getShoppingList("list.json")).thenReturn(List.of(item));
		when(credentialProvider.getUsername("CONTINENTE")).thenReturn("user");
		when(credentialProvider.getPassword("CONTINENTE")).thenReturn("pass");
		when(productService.findMappingsBySearchTextAndSupermarket("apples", "CONTINENTE")).thenReturn(List.of());
		when(supermarketDriver.searchProduct("apples")).thenReturn(new ArrayList<>(List.of(searchResult)));
		when(supermarketDriver.addProductToCart("sku123", 2)).thenReturn(true);

		service.startAutoBuy("list.json", "CONTINENTE", false);

		awaitState(AutoBuyState.AWAITING_MAPPING);
		productResolutionService.resolveMapping("sku123", false);

		awaitState(AutoBuyState.AWAITING_FINAL_REVIEW);

		service.completeRun(true);

		awaitState(AutoBuyState.SUCCESS);
	}

	@Test
	void testStartAutoBuy_InterruptedDuringExecution() throws InterruptedException {
		ShoppingItem item1 = new ShoppingItem("apples", 1);
		ShoppingItem item2 = new ShoppingItem("bananas", 2);

		when(shoppingListProvider.getShoppingList("list.json")).thenReturn(List.of(item1, item2));
		when(credentialProvider.getUsername("CONTINENTE")).thenReturn("user");
		when(credentialProvider.getPassword("CONTINENTE")).thenReturn("pass");

		// Mock resolveProduct to interrupt thread
		doAnswer(invocation -> {
			Thread.currentThread().interrupt();
			throw new InterruptedException("Simulated interrupt");
		}).when(productResolutionService).resolveProduct(any(), eq(item1), anyString());

		service.startAutoBuy("list.json", "CONTINENTE", false);

		awaitState(AutoBuyState.FAILED);
		assertTrue(executionContext.getStatus().error().contains("Execution interrupted"));
	}

	@Test
	void testCancelDuringExhaustedResolutionsState() {
		ShoppingItem item = new ShoppingItem("apples", 2);
		ProductMapping mapping = new ProductMapping("apples", "CONTINENTE", "sku123", "Red Apples");

		when(shoppingListProvider.getShoppingList("list.json")).thenReturn(List.of(item));
		when(credentialProvider.getUsername("CONTINENTE")).thenReturn("user");
		when(credentialProvider.getPassword("CONTINENTE")).thenReturn("pass");
		when(productService.findMappingsBySearchTextAndSupermarket("apples", "CONTINENTE"))
				.thenReturn(List.of(mapping));
		when(supermarketDriver.searchProduct("sku123")).thenReturn(List.of());
		when(supermarketDriver.searchProduct("apples")).thenReturn(List.of());

		service.startAutoBuy("list.json", "CONTINENTE", false);

		awaitState(AutoBuyState.AWAITING_EXHAUSTED_RESOLUTIONS);

		service.cancel();

		awaitState(AutoBuyState.FAILED);
		var status = executionContext.getStatus();
		assertTrue(status.error().contains("canceled by user"));
	}

	@Test
	void testStartAutoBuy_StateFailedDuringLoop() throws InterruptedException {
		ShoppingItem item1 = new ShoppingItem("apples", 1);
		ShoppingItem item2 = new ShoppingItem("bananas", 2);

		when(shoppingListProvider.getShoppingList("list.json")).thenReturn(List.of(item1, item2));
		when(credentialProvider.getUsername("CONTINENTE")).thenReturn("user");
		when(credentialProvider.getPassword("CONTINENTE")).thenReturn("pass");

		doAnswer(invocation -> {
			executionContext.transitionTo(AutoBuyState.FAILED);
			return null;
		}).when(productResolutionService).resolveProduct(any(), eq(item1), anyString());

		service.startAutoBuy("list.json", "CONTINENTE", false);

		awaitState(AutoBuyState.FAILED);
		verify(productResolutionService, never()).resolveProduct(any(), eq(item2), anyString());
	}

	@Test
	void testStartAutoBuy_WhenAwaitingFinalReviewAndActiveDriverCloseException() {
		// 1. Put into AWAITING_FINAL_REVIEW
		ShoppingItem item = new ShoppingItem("apples", 2);
		SearchResult searchResult = new SearchResult("sku123", "Red Apples", "Brand", BigDecimal.valueOf(1.99), "url",
				"Fruit");
		when(shoppingListProvider.getShoppingList("list.json")).thenReturn(List.of(item));
		when(credentialProvider.getUsername("CONTINENTE")).thenReturn("user");
		when(credentialProvider.getPassword("CONTINENTE")).thenReturn("pass");
		when(supermarketDriver.searchProduct("sku123")).thenReturn(List.of(searchResult));
		when(supermarketDriver.addProductToCart("sku123", 2)).thenReturn(true);
		when(productService.findMappingsBySearchTextAndSupermarket("apples", "CONTINENTE"))
				.thenReturn(List.of(new ProductMapping("apples", "CONTINENTE", "sku123", "Red Apples")));

		service.startAutoBuy("list.json", "CONTINENTE", false);
		awaitState(AutoBuyState.AWAITING_FINAL_REVIEW);

		// Make active driver close throw exception
		doThrow(new RuntimeException("close failed")).when(supermarketDriver).close();

		// Start new auto buy to trigger the state cleanup
		service.startAutoBuy("list.json", "CONTINENTE", false);

		// Verify it goes back to running or fails
		awaitState(AutoBuyState.AWAITING_FINAL_REVIEW);
	}
}
