package com.autobuy.service;

import com.autobuy.config.MemoryAppender;
import com.autobuy.driver.SupermarketDriver;
import com.autobuy.model.ProductMapping;
import com.autobuy.model.SearchResult;
import com.autobuy.model.ShoppingItem;
import com.autobuy.provider.CredentialProvider;
import com.autobuy.provider.ShoppingListProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.mockito.InOrder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.awaitility.Awaitility;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;
import com.autobuy.model.ResolutionAction;
import com.autobuy.model.AutoBuyState;

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

		service = new AutoBuyWebService(productService, priceHistoryService, List.of(supermarketDriver),
				credentialProvider, shoppingListProvider, taskExecutor);
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
		Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> service.getStatus().state() == targetState);
	}

	@Test
	void testGetStatusIdleByDefault() {
		var status = service.getStatus();
		assertEquals(AutoBuyState.IDLE, status.state());
		assertTrue(status.logs().isEmpty());
	}

	@Test
	void testStartAutoBuy_DriverNotFound() {
		service.startAutoBuy("list.json", "NONEXISTENT", false);
		awaitState(AutoBuyState.FAILED);
		var status = service.getStatus();
		assertTrue(status.error().contains("No driver found"));
	}

	@Test
	void testStartAutoBuy_EmptyShoppingList() {
		when(shoppingListProvider.getShoppingList("list.json")).thenReturn(List.of());

		service.startAutoBuy("list.json", "CONTINENTE", false);
		awaitState(AutoBuyState.FAILED);
		var status = service.getStatus();
		assertTrue(status.error().contains("Shopping list is empty"));
	}

	@Test
	void testStartAutoBuy_MissingCredentials() {
		when(shoppingListProvider.getShoppingList("list.json")).thenReturn(List.of(new ShoppingItem("apples", 1)));
		when(credentialProvider.getUsername("CONTINENTE")).thenReturn("");
		when(credentialProvider.getPassword("CONTINENTE")).thenReturn("");

		service.startAutoBuy("list.json", "CONTINENTE", false);
		awaitState(AutoBuyState.FAILED);
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
		awaitState(AutoBuyState.FAILED);
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
		when(productService.findMappingsBySearchTextAndSupermarket("apples", "CONTINENTE"))
				.thenReturn(List.of(mapping));
		when(supermarketDriver.searchProduct("sku123")).thenReturn(List.of(searchResult));
		when(supermarketDriver.isProductAvailable("sku123")).thenReturn(true);
		when(supermarketDriver.addProductToCart("sku123", 2)).thenReturn(true);

		service.startAutoBuy("list.json", "CONTINENTE", false);

		// The service will initialize and login, find mapping, add to cart, and then
		// transition to final review
		awaitState(AutoBuyState.AWAITING_FINAL_REVIEW);

		// Assert status details during the run
		var status = service.getStatus();
		assertEquals("apples", status.currentItemQuery());
		assertEquals(2, status.currentItemQuantity());

		// Complete the run
		service.completeRun();

		// Should transition to success
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

		// Unmapped item triggers immediate pause
		awaitState(AutoBuyState.AWAITING_MAPPING);

		// Resolve mapping for apples
		service.resolveMapping("skuB", true);

		// Should complete the run
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

		// Skip this item
		service.resolveMapping("skip", false);

		// Should skip and complete the run (move to final review since shopping list
		// only had 1 item)
		awaitState(AutoBuyState.AWAITING_FINAL_REVIEW);

		// Verify no mapping was saved and no item was added to cart
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

		com.autobuy.web.dto.ResolutionResultStatus status = service.resolveMapping("skuB", true);

		assertTrue(status.added());
		AutoBuyState currentState = service.getStatus().state();
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

		// Cancel execution
		service.cancel();

		awaitState(AutoBuyState.FAILED);
		var status = service.getStatus();
		assertTrue(status.error().contains("canceled by user"));
		verify(supermarketDriver, timeout(2000).atLeastOnce()).close();
	}

	@Test
	void testIllegalStateTransitions() {
		// Cannot resolve mapping when IDLE
		assertThrows(IllegalStateException.class, () -> service.resolveMapping("sku123", true));

		// Cannot complete run when IDLE
		assertThrows(IllegalStateException.class, () -> service.completeRun());

		// Cannot refine search when IDLE
		assertThrows(IllegalStateException.class, () -> service.refineSearch("query"));

		// Cannot start auto buy twice
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

		// Item B (bananas) is unmapped, so it runs first and pauses for mapping
		// immediately (mid-run)
		awaitState(AutoBuyState.AWAITING_MAPPING);
		assertEquals("bananas", service.getStatus().currentItemQuery());

		// Resolve mapping for bananas
		service.resolveMapping("skuB", true);

		// Then mapped items (apples, carrots) process automatically and transition to
		// final review
		awaitState(AutoBuyState.AWAITING_FINAL_REVIEW);

		// Complete run
		service.completeRun();
		awaitState(AutoBuyState.SUCCESS);

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

		// All mapped, no interactive prompt triggered, transitions straight to final
		// review
		awaitState(AutoBuyState.AWAITING_FINAL_REVIEW);

		service.completeRun();
		awaitState(AutoBuyState.SUCCESS);

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

		when(productService.findMappingsBySearchTextAndSupermarket("apples", "CONTINENTE")).thenReturn(List.of());
		when(productService.findMappingsBySearchTextAndSupermarket("bananas", "CONTINENTE")).thenReturn(List.of());

		when(supermarketDriver.searchProduct("apples")).thenReturn(new ArrayList<>(List.of(resA)));
		when(supermarketDriver.searchProduct("bananas")).thenReturn(new ArrayList<>(List.of(resB)));
		when(supermarketDriver.addProductToCart(anyString(), anyInt())).thenReturn(true);

		service.startAutoBuy("list.json", "CONTINENTE", false);

		// First unmapped item (apples) pauses immediately mid-run
		awaitState(AutoBuyState.AWAITING_MAPPING);
		assertEquals("apples", service.getStatus().currentItemQuery());
		service.resolveMapping("skuA", true);

		// Second unmapped item (bananas) pauses immediately mid-run
		awaitState(AutoBuyState.AWAITING_MAPPING);
		assertEquals("bananas", service.getStatus().currentItemQuery());
		service.resolveMapping("skuB", true);

		awaitState(AutoBuyState.AWAITING_FINAL_REVIEW);
		service.completeRun();
		awaitState(AutoBuyState.SUCCESS);

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

		when(productService.findMappingsBySearchTextAndSupermarket("apples", "CONTINENTE"))
				.thenReturn(List.of(mappingA));
		when(productService.findMappingsBySearchTextAndSupermarket("bananas", "CONTINENTE")).thenReturn(List.of());
		when(productService.findMappingsBySearchTextAndSupermarket("carrots", "CONTINENTE")).thenReturn(List.of());
		when(productService.findMappingsBySearchTextAndSupermarket("dates", "CONTINENTE"))
				.thenReturn(List.of(mappingD));
		when(productService.findMappingsBySearchTextAndSupermarket("eggplant", "CONTINENTE")).thenReturn(List.of());

		when(supermarketDriver.searchProduct("bananas")).thenReturn(new ArrayList<>(List.of(resB)));
		when(supermarketDriver.searchProduct("carrots")).thenReturn(new ArrayList<>(List.of(resC)));
		when(supermarketDriver.searchProduct("eggplant")).thenReturn(new ArrayList<>(List.of(resE)));
		when(supermarketDriver.searchProduct("skuA")).thenReturn(List.of(resA));
		when(supermarketDriver.isProductAvailable("skuA")).thenReturn(true);
		when(supermarketDriver.searchProduct("skuD")).thenReturn(List.of(resD));
		when(supermarketDriver.isProductAvailable("skuD")).thenReturn(true);
		when(supermarketDriver.addProductToCart(anyString(), anyInt())).thenReturn(true);

		service.startAutoBuy("list.json", "CONTINENTE", false);

		// Process unmapped in relative order: bananas, carrots, eggplant
		awaitState(AutoBuyState.AWAITING_MAPPING);
		assertEquals("bananas", service.getStatus().currentItemQuery());
		service.resolveMapping("skuB", true);

		awaitState(AutoBuyState.AWAITING_MAPPING);
		assertEquals("carrots", service.getStatus().currentItemQuery());
		service.resolveMapping("skuC", true);

		awaitState(AutoBuyState.AWAITING_MAPPING);
		assertEquals("eggplant", service.getStatus().currentItemQuery());
		service.resolveMapping("skuE", true);

		// Then mapped: apples, dates
		awaitState(AutoBuyState.AWAITING_FINAL_REVIEW);
		service.completeRun();
		awaitState(AutoBuyState.SUCCESS);

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
		when(productService.findMappingsBySearchTextAndSupermarket("apples", "CONTINENTE"))
				.thenReturn(List.of(mapping));
		// Mock SKU search returning empty list -> SKU not found
		when(supermarketDriver.searchProduct("sku123")).thenReturn(List.of());
		when(supermarketDriver.searchProduct("apples")).thenReturn(List.of());

		service.startAutoBuy("list.json", "CONTINENTE", false);

		// Should transition straight to exhausted resolutions
		awaitState(AutoBuyState.AWAITING_EXHAUSTED_RESOLUTIONS);

		// Skip it
		service.resolveMapping("skip", false);

		awaitState(AutoBuyState.AWAITING_FINAL_REVIEW);

		var status = service.getStatus();
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
		// Mock SKU unavailable
		when(supermarketDriver.isProductAvailable("sku123")).thenReturn(false);
		when(supermarketDriver.searchProduct("apples")).thenReturn(List.of());

		service.startAutoBuy("list.json", "CONTINENTE", false);

		// Should transition straight to exhausted resolutions
		awaitState(AutoBuyState.AWAITING_EXHAUSTED_RESOLUTIONS);

		// Skip it
		service.resolveMapping("skip", false);

		awaitState(AutoBuyState.AWAITING_FINAL_REVIEW);

		var status = service.getStatus();
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
		// Mock cart add failure
		when(supermarketDriver.addProductToCart("sku123", 2)).thenReturn(false);
		when(supermarketDriver.searchProduct("apples")).thenReturn(List.of());

		service.startAutoBuy("list.json", "CONTINENTE", false);

		// Should transition to exhausted resolutions because cart add failed
		awaitState(AutoBuyState.AWAITING_EXHAUSTED_RESOLUTIONS);

		// Skip it
		service.resolveMapping("skip", false);

		awaitState(AutoBuyState.AWAITING_FINAL_REVIEW);

		var status = service.getStatus();
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

		// Mock navigateToCart throwing an exception to test the catch block
		doThrow(new RuntimeException("Navigation failed")).when(supermarketDriver).navigateToCart();

		service.startAutoBuy("list.json", "CONTINENTE", false);

		// The service should still transition to final review because the exception is
		// caught
		awaitState(AutoBuyState.AWAITING_FINAL_REVIEW);

		// Complete the run
		service.completeRun();

		// Should transition to success
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

		// First search query: "apples"
		when(supermarketDriver.searchProduct("apples")).thenReturn(new ArrayList<>(List.of(initialResult)));
		// Refined search query: "red apples"
		when(supermarketDriver.searchProduct("red apples")).thenReturn(new ArrayList<>(List.of(refinedResult)));
		when(supermarketDriver.addProductToCart("skuB", 2)).thenReturn(true);

		service.startAutoBuy("list.json", "CONTINENTE", false);

		// Pause for mapping
		awaitState(AutoBuyState.AWAITING_MAPPING);

		var status = service.getStatus();
		assertEquals(1, status.searchResults().size());
		assertEquals("skuA", status.searchResults().get(0).externalId());

		// Refine search query inline
		service.refineSearch("red apples");

		// Should trigger new search and pause again
		awaitState(AutoBuyState.AWAITING_MAPPING);

		status = service.getStatus();
		assertEquals(1, status.searchResults().size());
		assertEquals("skuB", status.searchResults().get(0).externalId());

		// Resolve mapping with skuB
		service.resolveMapping("skuB", true);

		awaitState(AutoBuyState.AWAITING_FINAL_REVIEW);

		// Verify database mapping was saved for original query "apples"
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

		// Mock saveMapping throwing an exception
		doThrow(new RuntimeException("Database error")).when(productService).saveMappingWithPriority("apples",
				"CONTINENTE", searchResult, 0);

		service.startAutoBuy("list.json", "CONTINENTE", false);

		// Pause for mapping
		awaitState(AutoBuyState.AWAITING_MAPPING);

		// Resolve mapping
		service.resolveMapping("sku123", true);

		// Should still complete because the exception is caught
		awaitState(AutoBuyState.AWAITING_FINAL_REVIEW);

		service.completeRun();
		awaitState(AutoBuyState.SUCCESS);
	}

	@Test
	void testPerformGuestSearch_CachesDriver() {
		List<SearchResult> expected = List.of(new SearchResult("sku1", "Test", "Brand", BigDecimal.ONE, "url", "cat"));
		when(supermarketDriver.searchProduct("query")).thenReturn(expected);

		// First call: should initialize and search
		List<SearchResult> results1 = service.performGuestSearch("query", "CONTINENTE");
		assertEquals(expected, results1);
		verify(supermarketDriver, times(1)).initialize(null, null, false);
		verify(supermarketDriver, times(1)).searchProduct("query");

		// Second call: should reuse cached driver without re-initializing
		List<SearchResult> results2 = service.performGuestSearch("query", "CONTINENTE");
		assertEquals(expected, results2);
		verify(supermarketDriver, times(1)).initialize(null, null, false); // still 1
		verify(supermarketDriver, times(2)).searchProduct("query");
	}

	@Test
	void testPerformGuestSearch_SelfHealsOnFailure() {
		when(supermarketDriver.searchProduct("fail")).thenThrow(new RuntimeException("Search failed"));

		// Perform guest search and expect exception
		assertThrows(RuntimeException.class, () -> service.performGuestSearch("fail", "CONTINENTE"));
		verify(supermarketDriver, times(1)).close(); // Should be closed on error

		// Next guest search should recreate/re-initialize the driver
		List<SearchResult> expected = List.of(new SearchResult("sku1", "Test", "Brand", BigDecimal.ONE, "url", "cat"));
		when(supermarketDriver.searchProduct("ok")).thenReturn(expected);

		List<SearchResult> results = service.performGuestSearch("ok", "CONTINENTE");
		assertEquals(expected, results);
		verify(supermarketDriver, times(2)).initialize(null, null, false); // Initialized again
	}

	@Test
	void testStartAutoBuy_ClosesGuestSearchDriver() {
		// Populate the cached guestSearchDriver
		service.performGuestSearch("query", "CONTINENTE");
		verify(supermarketDriver, times(1)).initialize(null, null, false);

		// Start auto buy: should close the guest search driver first
		ShoppingItem item = new ShoppingItem("apples", 2);
		when(shoppingListProvider.getShoppingList("list.json")).thenReturn(List.of(item));
		when(credentialProvider.getUsername("CONTINENTE")).thenReturn("user");
		when(credentialProvider.getPassword("CONTINENTE")).thenReturn("pass");
		when(productService.findMappingsBySearchTextAndSupermarket("apples", "CONTINENTE")).thenReturn(List.of());
		when(supermarketDriver.searchProduct("apples")).thenReturn(List.of());

		service.startAutoBuy("list.json", "CONTINENTE", false);
		verify(supermarketDriver, times(1)).close(); // guest search driver was closed
	}

	@Test
	void testShutdown_ClosesGuestSearchDriver() {
		service.performGuestSearch("query", "CONTINENTE");
		service.shutdown();
		verify(supermarketDriver, times(1)).close();
	}

	@Test
	void testPerformGuestSearch_NonexistentSupermarket() {
		assertThrows(IllegalArgumentException.class, () -> service.performGuestSearch("query", "NONEXISTENT"));
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

		com.autobuy.web.dto.ResolutionResultStatus status = service.resolveMapping("sku456", true);
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

		assertThrows(com.autobuy.exception.AutoBuyException.class, () -> service.resolveMapping("sku123", false));
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

		com.autobuy.web.dto.ResolutionResultStatus status = service.resolveMapping("sku123", true);
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

		// Resolve with a SKU that is not in the search results
		assertThrows(IllegalArgumentException.class, () -> service.resolveMapping("nonexistent-sku", false));
	}

	@Test
	void testStartAutoBuy_InteractiveResolutionSelect_SaveMappingThrowsException() {
		ShoppingItem item = new ShoppingItem("apples", 2);
		SearchResult searchResult = new SearchResult("sku123", "Red Apples", "Brand", BigDecimal.valueOf(1.99), "url",
				"Fruit");

		when(shoppingListProvider.getShoppingList("list.json")).thenReturn(List.of(item));
		when(credentialProvider.getUsername("CONTINENTE")).thenReturn("user");
		when(credentialProvider.getPassword("CONTINENTE")).thenReturn("pass");
		when(productService.findMappingsBySearchTextAndSupermarket("apples", "CONTINENTE")).thenReturn(List.of());
		when(supermarketDriver.searchProduct("apples")).thenReturn(new ArrayList<>(List.of(searchResult)));
		when(supermarketDriver.addProductToCart("sku123", 2)).thenReturn(true);

		// Make saveMappingWithPriority throw an exception
		doThrow(new RuntimeException("DB down")).when(productService).saveMappingWithPriority(anyString(), anyString(),
				any(), anyInt());

		service.startAutoBuy("list.json", "CONTINENTE", false);

		awaitState(AutoBuyState.AWAITING_MAPPING);

		// Resolve with saveMapping=true: should catch DB exception quietly and still
		// succeed adding to cart
		com.autobuy.web.dto.ResolutionResultStatus status = service.resolveMapping("sku123", true);
		assertTrue(status.added());
	}

	@Test
	void testShutdown_WithActiveDriver() throws Exception {
		java.lang.reflect.Field field = AutoBuyWebService.class.getDeclaredField("activeDriver");
		field.setAccessible(true);
		field.set(service, supermarketDriver);

		service.shutdown();

		verify(supermarketDriver).close();
	}

	@Test
	void testShutdown_WithActiveDriverCloseException() throws Exception {
		doThrow(new RuntimeException("close failed")).when(supermarketDriver).close();

		java.lang.reflect.Field field = AutoBuyWebService.class.getDeclaredField("activeDriver");
		field.setAccessible(true);
		field.set(service, supermarketDriver);

		// Should catch exception quietly
		service.shutdown();

		verify(supermarketDriver).close();
	}

	@Test
	void testRefineSearch_NullOrBlankQuery() {
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

		assertThrows(IllegalArgumentException.class, () -> service.refineSearch(null));
		assertThrows(IllegalArgumentException.class, () -> service.refineSearch(" "));
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
		service.resolveMapping("sku123", false);

		awaitState(AutoBuyState.AWAITING_FINAL_REVIEW);

		service.completeRun(true);

		awaitState(AutoBuyState.SUCCESS);
	}

	@Test
	void testStartAutoBuy_RefineSearch() {
		ShoppingItem item = new ShoppingItem("apples", 2);
		SearchResult result1 = new SearchResult("sku1", "Red Apples", "Brand", BigDecimal.valueOf(1.9), "url", "Fruit");
		SearchResult result2 = new SearchResult("sku2", "Green Apples", "Brand", BigDecimal.valueOf(2.0), "url",
				"Fruit");

		when(shoppingListProvider.getShoppingList("list.json")).thenReturn(List.of(item));
		when(credentialProvider.getUsername("CONTINENTE")).thenReturn("user");
		when(credentialProvider.getPassword("CONTINENTE")).thenReturn("pass");
		when(productService.findMappingsBySearchTextAndSupermarket("apples", "CONTINENTE")).thenReturn(List.of());

		// First search returns result1
		when(supermarketDriver.searchProduct("apples")).thenReturn(new ArrayList<>(List.of(result1)));
		// Second search (refined) returns result2
		when(supermarketDriver.searchProduct("green apples")).thenReturn(new ArrayList<>(List.of(result2)));

		when(supermarketDriver.addProductToCart("sku2", 2)).thenReturn(true);

		service.startAutoBuy("list.json", "CONTINENTE", false);
		awaitState(AutoBuyState.AWAITING_MAPPING);

		// Refine search with "green apples"
		service.refineSearch("green apples");
		awaitState(AutoBuyState.AWAITING_MAPPING);

		// Resolve mapping with sku2
		service.resolveMapping("sku2", true);
		awaitState(AutoBuyState.AWAITING_FINAL_REVIEW);

		service.completeRun();
		awaitState(AutoBuyState.SUCCESS);
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
		// Cart add fails for resolved alternative
		when(supermarketDriver.addProductToCart("sku456", 2)).thenReturn(false);

		service.startAutoBuy("list.json", "CONTINENTE", false);
		awaitState(AutoBuyState.AWAITING_EXHAUSTED_RESOLUTIONS);

		// Resolve mapping with saveMapping = false
		service.resolveMapping("sku456", false);

		// Since cart add failed, it is skipped and deferred to skippedItems list
		awaitState(AutoBuyState.AWAITING_FINAL_REVIEW);

		var status = service.getStatus();
		assertEquals(1, status.skippedItems().size());
		assertEquals("apples", status.skippedItems().get(0));

		service.completeRun();
		awaitState(AutoBuyState.SUCCESS);
	}

	@Test
	void testStartAutoBuy_ExhaustedResolution_ExceptionsCaughtQuietly() {
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
		when(supermarketDriver.addProductToCart("sku456", 2)).thenReturn(true);

		// Make logPrice throw exception
		doThrow(new RuntimeException("DB error")).when(priceHistoryService).logPrice(any(), anyString());
		// Make saveMappingWithPriority throw exception
		doThrow(new RuntimeException("DB error")).when(productService).saveMappingWithPriority(anyString(), anyString(),
				any(), anyInt());

		service.startAutoBuy("list.json", "CONTINENTE", false);
		awaitState(AutoBuyState.AWAITING_EXHAUSTED_RESOLUTIONS);

		// Resolve mapping with saveMapping = true
		service.resolveMapping("sku456", true);

		awaitState(AutoBuyState.AWAITING_FINAL_REVIEW);

		service.completeRun();
		awaitState(AutoBuyState.SUCCESS);
	}

	@Test
	@SuppressWarnings("unchecked")
	void testStartAutoBuy_SelectNonexistentSkuReflection() throws Exception {
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

		// Get currentMappingFuture using reflection
		java.lang.reflect.Field field = AutoBuyWebService.class.getDeclaredField("currentMappingFuture");
		field.setAccessible(true);
		CompletableFuture<ResolutionAction> future = (CompletableFuture<ResolutionAction>) field.get(service);

		// Complete the future with SELECT of nonexistent-sku
		future.complete(new ResolutionAction(ResolutionAction.ActionType.SELECT, "nonexistent-sku", false));

		// The background thread should log a warning about nonexistent SKU and prompt
		// the user again
		awaitState(AutoBuyState.AWAITING_MAPPING);

		// Clean up by skipping
		service.resolveMapping("skip", false);
		awaitState(AutoBuyState.AWAITING_FINAL_REVIEW);
		service.completeRun();
		awaitState(AutoBuyState.SUCCESS);
	}

	@Test
	void testInterruptDuringRunningState() {
		ShoppingItem item = new ShoppingItem("apples", 2);
		when(shoppingListProvider.getShoppingList("list.json")).thenReturn(List.of(item));
		when(credentialProvider.getUsername("CONTINENTE")).thenReturn("user");
		when(credentialProvider.getPassword("CONTINENTE")).thenReturn("pass");
		when(productService.findMappingsBySearchTextAndSupermarket("apples", "CONTINENTE")).thenReturn(List.of());
		when(supermarketDriver.searchProduct("apples")).thenAnswer(invocation -> {
			Thread.currentThread().interrupt();
			throw new InterruptedException("Simulated interruption during RUNNING state");
		});

		service.startAutoBuy("list.json", "CONTINENTE", false);

		awaitState(AutoBuyState.FAILED);
		var status = service.getStatus();
		assertTrue(status.error().contains("Execution interrupted") || status.error().contains("interrupted"));
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
		var status = service.getStatus();
		assertTrue(status.error().contains("canceled by user"));
	}
}
