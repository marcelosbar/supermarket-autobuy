package com.autobuy.web;

import com.autobuy.driver.SupermarketDriver;
import com.autobuy.model.ProductMapping;
import com.autobuy.model.SearchResult;
import com.autobuy.model.ShoppingItem;
import com.autobuy.provider.CredentialProvider;
import com.autobuy.provider.ShoppingListProvider;
import com.autobuy.service.PriceHistoryService;
import com.autobuy.service.ProductService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.mockito.InOrder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.awaitility.Awaitility;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AutoBuyWebServiceTest {

	private ProductService productService;
	private PriceHistoryService priceHistoryService;
	private SupermarketDriver supermarketDriver;
	private CredentialProvider credentialProvider;
	private ShoppingListProvider shoppingListProvider;
	private ThreadPoolTaskExecutor taskExecutor;

	private AutoBuyWebService service;

	@BeforeEach
	void setUp() {
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

		service = new AutoBuyWebService(productService, priceHistoryService, List.of(supermarketDriver),
				credentialProvider, shoppingListProvider, taskExecutor);
	}

	@AfterEach
	void tearDown() {
		taskExecutor.shutdown();
	}

	private void awaitState(AutoBuyWebService.AutoBuyState targetState) {
		Awaitility.await().atMost(3, TimeUnit.SECONDS).until(() -> service.getStatus().state() == targetState);
	}

	@Test
	void testGetStatusIdleByDefault() {
		var status = service.getStatus();
		assertEquals(AutoBuyWebService.AutoBuyState.IDLE, status.state());
		assertTrue(status.logs().isEmpty());
	}

	@Test
	void testStartAutoBuy_DriverNotFound() {
		service.startAutoBuy("list.json", "NONEXISTENT", false);
		awaitState(AutoBuyWebService.AutoBuyState.FAILED);
		var status = service.getStatus();
		assertTrue(status.error().contains("No driver found"));
	}

	@Test
	void testStartAutoBuy_EmptyShoppingList() {
		when(shoppingListProvider.getShoppingList("list.json")).thenReturn(List.of());

		service.startAutoBuy("list.json", "CONTINENTE", false);
		awaitState(AutoBuyWebService.AutoBuyState.FAILED);
		var status = service.getStatus();
		assertTrue(status.error().contains("Shopping list is empty"));
	}

	@Test
	void testStartAutoBuy_MissingCredentials() {
		when(shoppingListProvider.getShoppingList("list.json")).thenReturn(List.of(new ShoppingItem("apples", 1)));
		when(credentialProvider.getUsername("CONTINENTE")).thenReturn("");
		when(credentialProvider.getPassword("CONTINENTE")).thenReturn("");

		service.startAutoBuy("list.json", "CONTINENTE", false);
		awaitState(AutoBuyWebService.AutoBuyState.FAILED);
		var status = service.getStatus();
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
		awaitState(AutoBuyWebService.AutoBuyState.FAILED);
		var status = service.getStatus();
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
		when(productService.findMappingBySearchTextAndSupermarket("apples", "CONTINENTE"))
				.thenReturn(Optional.of(mapping));
		when(supermarketDriver.searchProduct("sku123")).thenReturn(List.of(searchResult));
		when(supermarketDriver.addProductToCart("sku123", 2)).thenReturn(true);

		service.startAutoBuy("list.json", "CONTINENTE", false);

		// The service will initialize and login, find mapping, add to cart, and then
		// transition to final review
		awaitState(AutoBuyWebService.AutoBuyState.AWAITING_FINAL_REVIEW);

		// Assert status details during the run
		var status = service.getStatus();
		assertEquals("apples", status.currentItemQuery());
		assertEquals(2, status.currentItemQuantity());

		// Complete the run
		service.completeRun();

		// Should transition to success
		awaitState(AutoBuyWebService.AutoBuyState.SUCCESS);

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
		when(productService.findMappingBySearchTextAndSupermarket("apples", "CONTINENTE")).thenReturn(Optional.empty());
		when(supermarketDriver.searchProduct("apples"))
				.thenReturn(new ArrayList<>(List.of(searchResult1, searchResult2)));
		when(supermarketDriver.addProductToCart("skuB", 2)).thenReturn(true);

		service.startAutoBuy("list.json", "CONTINENTE", false);

		// The service will search, find no mapping, perform store search, and pause for
		// mapping resolution
		awaitState(AutoBuyWebService.AutoBuyState.AWAITING_MAPPING);

		var status = service.getStatus();
		assertEquals(2, status.searchResults().size());
		assertEquals("skuA", status.searchResults().get(0).externalId());

		// Resolve mapping with skuB
		service.resolveMapping("skuB");

		// The service will save new mapping, add to cart, and then go to final review
		awaitState(AutoBuyWebService.AutoBuyState.AWAITING_FINAL_REVIEW);

		// Verify database mapping was saved
		verify(productService).saveMapping("apples", "CONTINENTE", searchResult2);

		// Complete
		service.completeRun();
		awaitState(AutoBuyWebService.AutoBuyState.SUCCESS);
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
		when(productService.findMappingBySearchTextAndSupermarket("apples", "CONTINENTE")).thenReturn(Optional.empty());
		when(supermarketDriver.searchProduct("apples")).thenReturn(new ArrayList<>(List.of(searchResult1)));

		service.startAutoBuy("list.json", "CONTINENTE", false);

		awaitState(AutoBuyWebService.AutoBuyState.AWAITING_MAPPING);

		// Skip this item
		service.resolveMapping("skip");

		// Should skip and complete the run (move to final review since shopping list
		// only had 1 item)
		awaitState(AutoBuyWebService.AutoBuyState.AWAITING_FINAL_REVIEW);

		// Verify no mapping was saved and no item was added to cart
		verify(productService, never()).saveMapping(anyString(), anyString(), any());
		verify(supermarketDriver, never()).addProductToCart(anyString(), anyInt());

		service.completeRun();
		awaitState(AutoBuyWebService.AutoBuyState.SUCCESS);
		verify(supermarketDriver, timeout(2000).atLeastOnce()).close();
	}

	@Test
	void testCancelRun() {
		ShoppingItem item = new ShoppingItem("apples", 2);
		when(shoppingListProvider.getShoppingList("list.json")).thenReturn(List.of(item));
		when(credentialProvider.getUsername("CONTINENTE")).thenReturn("user");
		when(credentialProvider.getPassword("CONTINENTE")).thenReturn("pass");
		when(productService.findMappingBySearchTextAndSupermarket("apples", "CONTINENTE")).thenReturn(Optional.empty());
		when(supermarketDriver.searchProduct("apples")).thenReturn(
				new ArrayList<>(List.of(new SearchResult("skuA", "Name", "Brand", BigDecimal.ONE, "url", "cat"))));

		service.startAutoBuy("list.json", "CONTINENTE", false);

		awaitState(AutoBuyWebService.AutoBuyState.AWAITING_MAPPING);

		// Cancel execution
		service.cancel();

		awaitState(AutoBuyWebService.AutoBuyState.FAILED);
		var status = service.getStatus();
		assertTrue(status.error().contains("canceled by user"));
		verify(supermarketDriver, timeout(2000).atLeastOnce()).close();
	}

	@Test
	void testIllegalStateTransitions() {
		// Cannot resolve mapping when IDLE
		assertThrows(IllegalStateException.class, () -> service.resolveMapping("sku123"));

		// Cannot complete run when IDLE
		assertThrows(IllegalStateException.class, () -> service.completeRun());

		// Cannot start auto buy twice
		ShoppingItem item = new ShoppingItem("apples", 2);
		when(shoppingListProvider.getShoppingList("list.json")).thenReturn(List.of(item));
		when(credentialProvider.getUsername("CONTINENTE")).thenReturn("user");
		when(credentialProvider.getPassword("CONTINENTE")).thenReturn("pass");
		when(productService.findMappingBySearchTextAndSupermarket("apples", "CONTINENTE")).thenReturn(Optional.empty());
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

		when(productService.findMappingBySearchTextAndSupermarket("apples", "CONTINENTE"))
				.thenReturn(Optional.of(mappingA));
		when(productService.findMappingBySearchTextAndSupermarket("bananas", "CONTINENTE"))
				.thenReturn(Optional.empty());
		when(productService.findMappingBySearchTextAndSupermarket("carrots", "CONTINENTE"))
				.thenReturn(Optional.of(mappingC));

		when(supermarketDriver.searchProduct("bananas")).thenReturn(new ArrayList<>(List.of(resB)));
		when(supermarketDriver.searchProduct("skuA")).thenReturn(List.of(resA));
		when(supermarketDriver.searchProduct("skuC")).thenReturn(List.of(resC));

		when(supermarketDriver.addProductToCart(anyString(), anyInt())).thenReturn(true);

		service.startAutoBuy("list.json", "CONTINENTE", false);

		// Item B (bananas) is unmapped, so it runs first and pauses for mapping
		awaitState(AutoBuyWebService.AutoBuyState.AWAITING_MAPPING);
		assertEquals("bananas", service.getStatus().currentItemQuery());

		// Resolve mapping for bananas
		service.resolveMapping("skuB");

		// Then mapped items (apples, carrots) process automatically and transition to
		// final review
		awaitState(AutoBuyWebService.AutoBuyState.AWAITING_FINAL_REVIEW);

		// Complete run
		service.completeRun();
		awaitState(AutoBuyWebService.AutoBuyState.SUCCESS);

		// Verify order of calls
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

		when(productService.findMappingBySearchTextAndSupermarket("apples", "CONTINENTE"))
				.thenReturn(Optional.of(mappingA));
		when(productService.findMappingBySearchTextAndSupermarket("carrots", "CONTINENTE"))
				.thenReturn(Optional.of(mappingC));

		when(supermarketDriver.searchProduct("skuA")).thenReturn(List.of(resA));
		when(supermarketDriver.searchProduct("skuC")).thenReturn(List.of(resC));
		when(supermarketDriver.addProductToCart(anyString(), anyInt())).thenReturn(true);

		service.startAutoBuy("list.json", "CONTINENTE", false);

		// All mapped, no interactive prompt triggered, transitions straight to final
		// review
		awaitState(AutoBuyWebService.AutoBuyState.AWAITING_FINAL_REVIEW);

		service.completeRun();
		awaitState(AutoBuyWebService.AutoBuyState.SUCCESS);

		// Verify order of calls
		InOrder inOrder = inOrder(supermarketDriver);
		inOrder.verify(supermarketDriver).searchProduct("skuA");
		inOrder.verify(supermarketDriver).addProductToCart("skuA", 1);
		inOrder.verify(supermarketDriver).searchProduct("skuC");
		inOrder.verify(supermarketDriver).addProductToCart("skuC", 3);
		inOrder.verify(supermarketDriver).navigateToCart();
	}

	@Test
	void testProcessingOrder_AllUnmapped() {
		ShoppingItem itemA = new ShoppingItem("apples", 1);
		ShoppingItem itemB = new ShoppingItem("bananas", 2);

		SearchResult resA = new SearchResult("skuA", "Apples", "Brand", BigDecimal.ONE, "url", "Fruit");
		SearchResult resB = new SearchResult("skuB", "Bananas", "Brand", BigDecimal.ONE, "url", "Fruit");

		when(shoppingListProvider.getShoppingList("list.json")).thenReturn(List.of(itemA, itemB));
		when(credentialProvider.getUsername("CONTINENTE")).thenReturn("user");
		when(credentialProvider.getPassword("CONTINENTE")).thenReturn("pass");

		when(productService.findMappingBySearchTextAndSupermarket("apples", "CONTINENTE")).thenReturn(Optional.empty());
		when(productService.findMappingBySearchTextAndSupermarket("bananas", "CONTINENTE"))
				.thenReturn(Optional.empty());

		when(supermarketDriver.searchProduct("apples")).thenReturn(new ArrayList<>(List.of(resA)));
		when(supermarketDriver.searchProduct("bananas")).thenReturn(new ArrayList<>(List.of(resB)));
		when(supermarketDriver.addProductToCart(anyString(), anyInt())).thenReturn(true);

		service.startAutoBuy("list.json", "CONTINENTE", false);

		// First unmapped item (apples) pauses
		awaitState(AutoBuyWebService.AutoBuyState.AWAITING_MAPPING);
		assertEquals("apples", service.getStatus().currentItemQuery());
		service.resolveMapping("skuA");

		// Second unmapped item (bananas) pauses
		awaitState(AutoBuyWebService.AutoBuyState.AWAITING_MAPPING);
		assertEquals("bananas", service.getStatus().currentItemQuery());
		service.resolveMapping("skuB");

		awaitState(AutoBuyWebService.AutoBuyState.AWAITING_FINAL_REVIEW);
		service.completeRun();
		awaitState(AutoBuyWebService.AutoBuyState.SUCCESS);

		// Verify order of calls
		InOrder inOrder = inOrder(supermarketDriver);
		inOrder.verify(supermarketDriver).searchProduct("apples");
		inOrder.verify(supermarketDriver).addProductToCart("skuA", 1);
		inOrder.verify(supermarketDriver).searchProduct("bananas");
		inOrder.verify(supermarketDriver).addProductToCart("skuB", 2);
		inOrder.verify(supermarketDriver).navigateToCart();
	}

	@Test
	void testProcessingOrder_PreservesRelativeOrder() {
		ShoppingItem itemA = new ShoppingItem("apples", 1);
		ShoppingItem itemB = new ShoppingItem("bananas", 2);
		ShoppingItem itemC = new ShoppingItem("carrots", 3);
		ShoppingItem itemD = new ShoppingItem("dates", 4);
		ShoppingItem itemE = new ShoppingItem("eggplant", 5);

		ProductMapping mappingA = new ProductMapping("apples", "CONTINENTE", "skuA", "Apples");
		ProductMapping mappingD = new ProductMapping("dates", "CONTINENTE", "skuD", "Dates");

		SearchResult resA = new SearchResult("skuA", "Apples", "Brand", BigDecimal.ONE, "url", "Fruit");
		SearchResult resB = new SearchResult("skuB", "Bananas", "Brand", BigDecimal.ONE, "url", "Fruit");
		SearchResult resC = new SearchResult("skuC", "Carrots", "Brand", BigDecimal.ONE, "url", "Fruit");
		SearchResult resD = new SearchResult("skuD", "Dates", "Brand", BigDecimal.ONE, "url", "Fruit");
		SearchResult resE = new SearchResult("skuE", "Eggplant", "Brand", BigDecimal.ONE, "url", "Fruit");

		when(shoppingListProvider.getShoppingList("list.json")).thenReturn(List.of(itemA, itemB, itemC, itemD, itemE));
		when(credentialProvider.getUsername("CONTINENTE")).thenReturn("user");
		when(credentialProvider.getPassword("CONTINENTE")).thenReturn("pass");

		when(productService.findMappingBySearchTextAndSupermarket("apples", "CONTINENTE"))
				.thenReturn(Optional.of(mappingA));
		when(productService.findMappingBySearchTextAndSupermarket("bananas", "CONTINENTE"))
				.thenReturn(Optional.empty());
		when(productService.findMappingBySearchTextAndSupermarket("carrots", "CONTINENTE"))
				.thenReturn(Optional.empty());
		when(productService.findMappingBySearchTextAndSupermarket("dates", "CONTINENTE"))
				.thenReturn(Optional.of(mappingD));
		when(productService.findMappingBySearchTextAndSupermarket("eggplant", "CONTINENTE"))
				.thenReturn(Optional.empty());

		when(supermarketDriver.searchProduct("bananas")).thenReturn(new ArrayList<>(List.of(resB)));
		when(supermarketDriver.searchProduct("carrots")).thenReturn(new ArrayList<>(List.of(resC)));
		when(supermarketDriver.searchProduct("eggplant")).thenReturn(new ArrayList<>(List.of(resE)));
		when(supermarketDriver.searchProduct("skuA")).thenReturn(List.of(resA));
		when(supermarketDriver.searchProduct("skuD")).thenReturn(List.of(resD));
		when(supermarketDriver.addProductToCart(anyString(), anyInt())).thenReturn(true);

		service.startAutoBuy("list.json", "CONTINENTE", false);

		// Process unmapped in relative order: bananas, carrots, eggplant
		awaitState(AutoBuyWebService.AutoBuyState.AWAITING_MAPPING);
		assertEquals("bananas", service.getStatus().currentItemQuery());
		service.resolveMapping("skuB");

		awaitState(AutoBuyWebService.AutoBuyState.AWAITING_MAPPING);
		assertEquals("carrots", service.getStatus().currentItemQuery());
		service.resolveMapping("skuC");

		awaitState(AutoBuyWebService.AutoBuyState.AWAITING_MAPPING);
		assertEquals("eggplant", service.getStatus().currentItemQuery());
		service.resolveMapping("skuE");

		// Then mapped: apples, dates
		awaitState(AutoBuyWebService.AutoBuyState.AWAITING_FINAL_REVIEW);
		service.completeRun();
		awaitState(AutoBuyWebService.AutoBuyState.SUCCESS);

		// Verify order of calls
		InOrder inOrder = inOrder(supermarketDriver);
		inOrder.verify(supermarketDriver).searchProduct("bananas");
		inOrder.verify(supermarketDriver).addProductToCart("skuB", 2);
		inOrder.verify(supermarketDriver).searchProduct("carrots");
		inOrder.verify(supermarketDriver).addProductToCart("skuC", 3);
		inOrder.verify(supermarketDriver).searchProduct("eggplant");
		inOrder.verify(supermarketDriver).addProductToCart("skuE", 5);
		inOrder.verify(supermarketDriver).searchProduct("skuA");
		inOrder.verify(supermarketDriver).addProductToCart("skuA", 1);
		inOrder.verify(supermarketDriver).searchProduct("skuD");
		inOrder.verify(supermarketDriver).addProductToCart("skuD", 4);
		inOrder.verify(supermarketDriver).navigateToCart();
	}

	@Test
	void testStartAutoBuy_MappedSkuNotFound() {
		ShoppingItem item = new ShoppingItem("apples", 2);
		ProductMapping mapping = new ProductMapping("apples", "CONTINENTE", "sku123", "Red Apples");

		when(shoppingListProvider.getShoppingList("list.json")).thenReturn(List.of(item));
		when(credentialProvider.getUsername("CONTINENTE")).thenReturn("user");
		when(credentialProvider.getPassword("CONTINENTE")).thenReturn("pass");
		when(productService.findMappingBySearchTextAndSupermarket("apples", "CONTINENTE"))
				.thenReturn(Optional.of(mapping));
		// Mock SKU search returning empty list -> SKU not found
		when(supermarketDriver.searchProduct("sku123")).thenReturn(List.of());

		service.startAutoBuy("list.json", "CONTINENTE", false);

		// Should transition straight to final review without pausing for mapping
		awaitState(AutoBuyWebService.AutoBuyState.AWAITING_FINAL_REVIEW);

		var status = service.getStatus();
		assertEquals(1, status.skippedItems().size());
		assertEquals("apples", status.skippedItems().get(0));

		service.completeRun();
		awaitState(AutoBuyWebService.AutoBuyState.SUCCESS);
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
		when(productService.findMappingBySearchTextAndSupermarket("apples", "CONTINENTE"))
				.thenReturn(Optional.of(mapping));
		when(supermarketDriver.searchProduct("sku123")).thenReturn(List.of(searchResult));
		// Mock SKU unavailable
		when(supermarketDriver.isProductAvailable("sku123")).thenReturn(false);

		service.startAutoBuy("list.json", "CONTINENTE", false);

		// Should transition straight to final review without pausing for mapping
		awaitState(AutoBuyWebService.AutoBuyState.AWAITING_FINAL_REVIEW);

		var status = service.getStatus();
		assertEquals(1, status.skippedItems().size());
		assertEquals("apples", status.skippedItems().get(0));

		service.completeRun();
		awaitState(AutoBuyWebService.AutoBuyState.SUCCESS);
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
		when(productService.findMappingBySearchTextAndSupermarket("apples", "CONTINENTE"))
				.thenReturn(Optional.of(mapping));
		when(supermarketDriver.searchProduct("sku123")).thenReturn(List.of(searchResult));
		when(supermarketDriver.isProductAvailable("sku123")).thenReturn(true);
		// Mock cart add failure
		when(supermarketDriver.addProductToCart("sku123", 2)).thenReturn(false);

		service.startAutoBuy("list.json", "CONTINENTE", false);

		// Should transition straight to final review
		awaitState(AutoBuyWebService.AutoBuyState.AWAITING_FINAL_REVIEW);

		var status = service.getStatus();
		assertEquals(1, status.skippedItems().size());
		assertEquals("apples", status.skippedItems().get(0));

		service.completeRun();
		awaitState(AutoBuyWebService.AutoBuyState.SUCCESS);
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
		when(productService.findMappingBySearchTextAndSupermarket("apples", "CONTINENTE"))
				.thenReturn(Optional.of(mapping));
		when(supermarketDriver.searchProduct("sku123")).thenReturn(List.of(searchResult));
		when(supermarketDriver.addProductToCart("sku123", 2)).thenReturn(true);

		// Mock navigateToCart throwing an exception to test the catch block
		doThrow(new RuntimeException("Navigation failed")).when(supermarketDriver).navigateToCart();

		service.startAutoBuy("list.json", "CONTINENTE", false);

		// The service should still transition to final review because the exception is
		// caught
		awaitState(AutoBuyWebService.AutoBuyState.AWAITING_FINAL_REVIEW);

		// Complete the run
		service.completeRun();

		// Should transition to success
		awaitState(AutoBuyWebService.AutoBuyState.SUCCESS);

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
		when(productService.findMappingBySearchTextAndSupermarket("apples", "CONTINENTE")).thenReturn(Optional.empty());

		// First search query: "apples"
		when(supermarketDriver.searchProduct("apples")).thenReturn(new ArrayList<>(List.of(initialResult)));
		// Refined search query: "red apples"
		when(supermarketDriver.searchProduct("red apples")).thenReturn(new ArrayList<>(List.of(refinedResult)));
		when(supermarketDriver.addProductToCart("skuB", 2)).thenReturn(true);

		service.startAutoBuy("list.json", "CONTINENTE", false);

		// Pause for mapping
		awaitState(AutoBuyWebService.AutoBuyState.AWAITING_MAPPING);

		var status = service.getStatus();
		assertEquals(1, status.searchResults().size());
		assertEquals("skuA", status.searchResults().get(0).externalId());

		// Refine search query inline
		service.refineSearch("red apples");

		// Should trigger new search and pause again
		awaitState(AutoBuyWebService.AutoBuyState.AWAITING_MAPPING);

		status = service.getStatus();
		assertEquals(1, status.searchResults().size());
		assertEquals("skuB", status.searchResults().get(0).externalId());

		// Resolve mapping with skuB
		service.resolveMapping("skuB");

		awaitState(AutoBuyWebService.AutoBuyState.AWAITING_FINAL_REVIEW);

		// Verify database mapping was saved for original query "apples"
		verify(productService).saveMapping("apples", "CONTINENTE", refinedResult);

		service.completeRun();
		awaitState(AutoBuyWebService.AutoBuyState.SUCCESS);
	}

	@Test
	void testStartAutoBuy_InteractiveResolutionSaveMappingException() {
		ShoppingItem item = new ShoppingItem("apples", 2);
		SearchResult searchResult = new SearchResult("sku123", "Red Apples", "Brand", BigDecimal.valueOf(1.99), "url",
				"Fruit");

		when(shoppingListProvider.getShoppingList("list.json")).thenReturn(List.of(item));
		when(credentialProvider.getUsername("CONTINENTE")).thenReturn("user");
		when(credentialProvider.getPassword("CONTINENTE")).thenReturn("pass");
		when(productService.findMappingBySearchTextAndSupermarket("apples", "CONTINENTE")).thenReturn(Optional.empty());
		when(supermarketDriver.searchProduct("apples")).thenReturn(new ArrayList<>(List.of(searchResult)));
		when(supermarketDriver.addProductToCart("sku123", 2)).thenReturn(true);

		// Mock saveMapping throwing an exception
		doThrow(new RuntimeException("Database error")).when(productService).saveMapping("apples", "CONTINENTE",
				searchResult);

		service.startAutoBuy("list.json", "CONTINENTE", false);

		// Pause for mapping
		awaitState(AutoBuyWebService.AutoBuyState.AWAITING_MAPPING);

		// Resolve mapping
		service.resolveMapping("sku123");

		// Should still complete because the exception is caught
		awaitState(AutoBuyWebService.AutoBuyState.AWAITING_FINAL_REVIEW);

		service.completeRun();
		awaitState(AutoBuyWebService.AutoBuyState.SUCCESS);
	}
}
