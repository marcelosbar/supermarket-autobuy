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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AutoBuyOrchestrationServiceTest {

	@Mock
	private ProductService productService;
	@Mock
	private PriceHistoryService priceHistoryService;
	@Mock
	private SupermarketDriver supermarketDriver;
	@Mock
	private CredentialProvider credentialProvider;
	@Mock
	private ShoppingListProvider shoppingListProvider;
	@Mock
	private GuestSearchService guestSearchService;

	private ThreadPoolTaskExecutor taskExecutor;

	private AutoBuyExecutionContext executionContext;
	private ProductResolutionService productResolutionService;
	private ExecutionProviders executionProviders;
	private AutoBuyOrchestrationService service;

	@BeforeEach
	void setUp() {
		MemoryAppender.clear();

		taskExecutor = new ThreadPoolTaskExecutor();
		taskExecutor.setCorePoolSize(2);
		taskExecutor.setMaxPoolSize(2);
		taskExecutor.setQueueCapacity(10);
		taskExecutor.setThreadNamePrefix("Test-AutoBuy-");
		taskExecutor.initialize();

		lenient().when(supermarketDriver.getSupermarketName()).thenReturn("CONTINENTE");
		lenient().when(supermarketDriver.isProductAvailable(anyString())).thenReturn(true);

		executionContext = new AutoBuyExecutionContext();
		productResolutionService = spy(
				new ProductResolutionService(productService, priceHistoryService, executionContext));
		executionProviders = new ExecutionProviders(credentialProvider, shoppingListProvider, guestSearchService);

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
		Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> executionContext.getState() == targetState);
	}

	@Test
	void startAutoBuy_driverNotFound_transitionsToFailedState() {
		// Act
		service.startAutoBuy("list.json", "NONEXISTENT", false);

		// Assert
		awaitState(AutoBuyState.FAILED);
		assertTrue(executionContext.getErrorMsg().contains("No driver found"));
		verify(guestSearchService).close();
	}

	@Test
	void startAutoBuy_emptyShoppingList_transitionsToFailedState() {
		// Arrange
		when(shoppingListProvider.getShoppingList("list.json")).thenReturn(List.of());

		// Act
		service.startAutoBuy("list.json", "CONTINENTE", false);

		// Assert
		awaitState(AutoBuyState.FAILED);
		assertTrue(executionContext.getErrorMsg().contains("Shopping list is empty"));
	}

	@Test
	void startAutoBuy_missingCredentials_transitionsToFailedState() {
		// Arrange
		when(shoppingListProvider.getShoppingList("list.json")).thenReturn(List.of(new ShoppingItem("apples", 1)));
		when(credentialProvider.getUsername("CONTINENTE")).thenReturn("");
		when(credentialProvider.getPassword("CONTINENTE")).thenReturn("");

		// Act
		service.startAutoBuy("list.json", "CONTINENTE", false);

		// Assert
		awaitState(AutoBuyState.FAILED);
		assertTrue(executionContext.getErrorMsg().contains("Credentials for CONTINENTE are not configured"));
	}

	@Test
	void startAutoBuy_driverInitFailure_transitionsToFailedState() {
		// Arrange
		when(shoppingListProvider.getShoppingList("list.json")).thenReturn(List.of(new ShoppingItem("apples", 1)));
		when(credentialProvider.getUsername("CONTINENTE")).thenReturn("user");
		when(credentialProvider.getPassword("CONTINENTE")).thenReturn("pass");
		doThrow(new RuntimeException("Playwright crashed")).when(supermarketDriver).initialize(anyString(), anyString(),
				anyBoolean());

		// Act
		service.startAutoBuy("list.json", "CONTINENTE", false);

		// Assert
		awaitState(AutoBuyState.FAILED);
		assertTrue(executionContext.getErrorMsg().contains("Failed to initialize driver"));
		verify(supermarketDriver, timeout(2000).atLeastOnce()).close();
	}

	@Test
	void startAutoBuy_validMappedItem_addsToCartAndCompletes() {
		// Arrange
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

		// Act
		service.startAutoBuy("list.json", "CONTINENTE", false);
		awaitState(AutoBuyState.AWAITING_FINAL_REVIEW);

		// Assert
		assertEquals("apples", executionContext.getCurrentItemQuery());
		assertEquals(2, executionContext.getCurrentItemQuantity());

		// Act
		service.completeRun();

		// Assert
		awaitState(AutoBuyState.SUCCESS);
		verify(priceHistoryService).logPrice(searchResult, "CONTINENTE");
		verify(supermarketDriver).navigateToCart();
		verify(supermarketDriver, timeout(2000).atLeastOnce()).close();
	}

	@Test
	void startAutoBuy_unmappedItem_awaitsMappingAndCompletesOnResolution() {
		// Arrange
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

		// Act
		service.startAutoBuy("list.json", "CONTINENTE", false);
		awaitState(AutoBuyState.AWAITING_MAPPING);

		productResolutionService.resolveMapping("skuB", true);
		awaitState(AutoBuyState.AWAITING_FINAL_REVIEW);

		// Assert
		verify(productService).saveMappingWithPriority("apples", "CONTINENTE", searchResult2, 0);
		verify(supermarketDriver).addProductToCart("skuB", 2);

		// Act
		service.completeRun();

		// Assert
		awaitState(AutoBuyState.SUCCESS);
		verify(supermarketDriver, timeout(2000).atLeastOnce()).close();
	}

	@Test
	void startAutoBuy_unmappedItemSkipped_completesWithoutAddingToCart() {
		// Arrange
		ShoppingItem item = new ShoppingItem("apples", 2);
		SearchResult searchResult1 = new SearchResult("skuA", "Green Apples", "Brand", BigDecimal.valueOf(1.5), "url",
				"Fruit");

		when(shoppingListProvider.getShoppingList("list.json")).thenReturn(List.of(item));
		when(credentialProvider.getUsername("CONTINENTE")).thenReturn("user");
		when(credentialProvider.getPassword("CONTINENTE")).thenReturn("pass");
		when(productService.findMappingsBySearchTextAndSupermarket("apples", "CONTINENTE")).thenReturn(List.of());
		when(supermarketDriver.searchProduct("apples")).thenReturn(new ArrayList<>(List.of(searchResult1)));

		// Act
		service.startAutoBuy("list.json", "CONTINENTE", false);
		awaitState(AutoBuyState.AWAITING_MAPPING);

		productResolutionService.resolveMapping("skip", false);
		awaitState(AutoBuyState.AWAITING_FINAL_REVIEW);

		// Assert
		verify(productService, never()).saveMappingWithPriority(anyString(), anyString(), any(), anyInt());
		verify(supermarketDriver, never()).addProductToCart(anyString(), anyInt());

		// Act
		service.completeRun();

		// Assert
		awaitState(AutoBuyState.SUCCESS);
		verify(supermarketDriver, timeout(2000).atLeastOnce()).close();
	}

	@Test
	void resolveMapping_validSku_transitionsStateAndAddsToCart() {
		// Arrange
		ShoppingItem item = new ShoppingItem("apples", 2);
		SearchResult searchResult = new SearchResult("skuB", "Red Apples", "Brand", BigDecimal.valueOf(1.9), "url",
				"Fruit");

		when(shoppingListProvider.getShoppingList("list.json")).thenReturn(List.of(item));
		when(credentialProvider.getUsername("CONTINENTE")).thenReturn("user");
		when(credentialProvider.getPassword("CONTINENTE")).thenReturn("pass");
		when(productService.findMappingsBySearchTextAndSupermarket("apples", "CONTINENTE")).thenReturn(List.of());
		when(supermarketDriver.searchProduct("apples")).thenReturn(new ArrayList<>(List.of(searchResult)));
		when(supermarketDriver.addProductToCart("skuB", 2)).thenReturn(true);

		// Act
		service.startAutoBuy("list.json", "CONTINENTE", false);
		awaitState(AutoBuyState.AWAITING_MAPPING);

		var status = productResolutionService.resolveMapping("skuB", true);

		// Assert
		assertTrue(status.added());
		AutoBuyState currentState = executionContext.getState();
		assertTrue(currentState == AutoBuyState.RUNNING || currentState == AutoBuyState.AWAITING_FINAL_REVIEW);

		// Act
		awaitState(AutoBuyState.AWAITING_FINAL_REVIEW);
		service.completeRun();
	}

	@Test
	void cancel_duringAwaitingMapping_transitionsToFailedState() {
		// Arrange
		ShoppingItem item = new ShoppingItem("apples", 2);
		when(shoppingListProvider.getShoppingList("list.json")).thenReturn(List.of(item));
		when(credentialProvider.getUsername("CONTINENTE")).thenReturn("user");
		when(credentialProvider.getPassword("CONTINENTE")).thenReturn("pass");
		when(productService.findMappingsBySearchTextAndSupermarket("apples", "CONTINENTE")).thenReturn(List.of());
		when(supermarketDriver.searchProduct("apples")).thenReturn(
				new ArrayList<>(List.of(new SearchResult("skuA", "Name", "Brand", BigDecimal.ONE, "url", "cat"))));

		service.startAutoBuy("list.json", "CONTINENTE", false);
		awaitState(AutoBuyState.AWAITING_MAPPING);

		// Act
		service.cancel();

		// Assert
		awaitState(AutoBuyState.FAILED);
		assertTrue(executionContext.getErrorMsg().contains("canceled by user"));
		verify(supermarketDriver, timeout(2000).atLeastOnce()).close();
	}

	@Test
	void resolveMapping_illegalState_throwsIllegalStateException() {
		// Arrange & Act & Assert
		assertThrows(IllegalStateException.class, () -> productResolutionService.resolveMapping("sku123", true));
		assertThrows(IllegalStateException.class, () -> service.completeRun());
		assertThrows(IllegalStateException.class, () -> productResolutionService.refineSearch("query"));

		executionContext.transitionTo(AutoBuyState.RUNNING);
		assertThrows(IllegalStateException.class, () -> service.startAutoBuy("list.json", "CONTINENTE", false));
	}

	@Test
	void startAutoBuy_mixedMappedAndUnmappedItems_processesUnmappedItemsFirst() {
		// Arrange
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

		// Act
		service.startAutoBuy("list.json", "CONTINENTE", false);
		awaitState(AutoBuyState.AWAITING_MAPPING);

		// Assert
		assertEquals("bananas", executionContext.getCurrentItemQuery());

		// Act
		productResolutionService.resolveMapping("skuB", true);
		awaitState(AutoBuyState.AWAITING_FINAL_REVIEW);

		service.completeRun();

		// Assert
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
	void startAutoBuy_allMappedItems_processesInOrderAndCompletes() {
		// Arrange
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

		// Act
		service.startAutoBuy("list.json", "CONTINENTE", false);
		awaitState(AutoBuyState.AWAITING_FINAL_REVIEW);

		service.completeRun();

		// Assert
		awaitState(AutoBuyState.SUCCESS);

		InOrder inOrder = inOrder(supermarketDriver);
		inOrder.verify(supermarketDriver).searchProduct("skuA");
		inOrder.verify(supermarketDriver).addProductToCart("skuA", 1);
		inOrder.verify(supermarketDriver).searchProduct("skuC");
		inOrder.verify(supermarketDriver).addProductToCart("skuC", 3);
		inOrder.verify(supermarketDriver).navigateToCart();
	}

	@Test
	void startAutoBuy_mappedSkuNotFound_awaitsExhaustedResolutions() {
		// Arrange
		ShoppingItem item = new ShoppingItem("apples", 2);
		ProductMapping mapping = new ProductMapping("apples", "CONTINENTE", "sku123", "Red Apples");

		when(shoppingListProvider.getShoppingList("list.json")).thenReturn(List.of(item));
		when(credentialProvider.getUsername("CONTINENTE")).thenReturn("user");
		when(credentialProvider.getPassword("CONTINENTE")).thenReturn("pass");
		when(productService.findMappingsBySearchTextAndSupermarket("apples", "CONTINENTE"))
				.thenReturn(List.of(mapping));
		when(supermarketDriver.searchProduct("sku123")).thenReturn(List.of());
		when(supermarketDriver.searchProduct("apples")).thenReturn(List.of());

		// Act
		service.startAutoBuy("list.json", "CONTINENTE", false);
		awaitState(AutoBuyState.AWAITING_EXHAUSTED_RESOLUTIONS);

		productResolutionService.resolveMapping("skip", false);
		awaitState(AutoBuyState.AWAITING_FINAL_REVIEW);

		// Assert
		assertEquals(1, executionContext.getSkippedItems().size());
		assertEquals("apples", executionContext.getSkippedItems().get(0));

		// Act
		service.completeRun();

		// Assert
		awaitState(AutoBuyState.SUCCESS);
	}

	@Test
	void startAutoBuy_mappedSkuUnavailable_awaitsExhaustedResolutions() {
		// Arrange
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

		// Act
		service.startAutoBuy("list.json", "CONTINENTE", false);
		awaitState(AutoBuyState.AWAITING_EXHAUSTED_RESOLUTIONS);

		productResolutionService.resolveMapping("skip", false);
		awaitState(AutoBuyState.AWAITING_FINAL_REVIEW);

		// Assert
		assertEquals(1, executionContext.getSkippedItems().size());
		assertEquals("apples", executionContext.getSkippedItems().get(0));

		// Act
		service.completeRun();

		// Assert
		awaitState(AutoBuyState.SUCCESS);
	}

	@Test
	void startAutoBuy_cartAddFailure_awaitsExhaustedResolutions() {
		// Arrange
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

		// Act
		service.startAutoBuy("list.json", "CONTINENTE", false);
		awaitState(AutoBuyState.AWAITING_EXHAUSTED_RESOLUTIONS);

		productResolutionService.resolveMapping("skip", false);
		awaitState(AutoBuyState.AWAITING_FINAL_REVIEW);

		// Assert
		assertEquals(1, executionContext.getSkippedItems().size());
		assertEquals("apples", executionContext.getSkippedItems().get(0));

		// Act
		service.completeRun();

		// Assert
		awaitState(AutoBuyState.SUCCESS);
	}

	@Test
	void startAutoBuy_navigateToCartFailure_completesSuccessfully() {
		// Arrange
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

		// Act
		service.startAutoBuy("list.json", "CONTINENTE", false);
		awaitState(AutoBuyState.AWAITING_FINAL_REVIEW);

		service.completeRun();

		// Assert
		awaitState(AutoBuyState.SUCCESS);
		verify(supermarketDriver).navigateToCart();
		verify(supermarketDriver, timeout(2000).atLeastOnce()).close();
	}

	@Test
	void startAutoBuy_interactiveRefining_updatesSearchResultsAndResolves() {
		// Arrange
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

		// Act
		service.startAutoBuy("list.json", "CONTINENTE", false);
		awaitState(AutoBuyState.AWAITING_MAPPING);

		// Assert
		assertEquals(1, executionContext.getSearchResults().size());
		assertEquals("skuA", executionContext.getSearchResults().get(0).externalId());

		// Act
		productResolutionService.refineSearch("red apples");
		awaitState(AutoBuyState.AWAITING_MAPPING);

		// Assert
		assertEquals(1, executionContext.getSearchResults().size());
		assertEquals("skuB", executionContext.getSearchResults().get(0).externalId());

		// Act
		productResolutionService.resolveMapping("skuB", true);
		awaitState(AutoBuyState.AWAITING_FINAL_REVIEW);

		// Assert
		verify(productService).saveMappingWithPriority("apples", "CONTINENTE", refinedResult, 0);

		// Act
		service.completeRun();

		// Assert
		awaitState(AutoBuyState.SUCCESS);
	}

	@Test
	void startAutoBuy_saveMappingThrowsException_continuesAndCompletes() {
		// Arrange
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

		// Act
		service.startAutoBuy("list.json", "CONTINENTE", false);
		awaitState(AutoBuyState.AWAITING_MAPPING);

		productResolutionService.resolveMapping("sku123", true);
		awaitState(AutoBuyState.AWAITING_FINAL_REVIEW);

		service.completeRun();

		// Assert
		awaitState(AutoBuyState.SUCCESS);
	}

	@Test
	void startAutoBuy_exhaustedResolutionSelect_addsAlternativeToCartAndCompletes() {
		// Arrange
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

		// Act
		service.startAutoBuy("list.json", "CONTINENTE", false);
		awaitState(AutoBuyState.AWAITING_EXHAUSTED_RESOLUTIONS);

		var status = productResolutionService.resolveMapping("sku456", true);

		// Assert
		assertTrue(status.added());

		// Act
		awaitState(AutoBuyState.AWAITING_FINAL_REVIEW);
		service.completeRun();

		// Assert
		awaitState(AutoBuyState.SUCCESS);
	}

	@Test
	void resolveMapping_cartAddFailureWithoutSaveMapping_throwsAutoBuyException() {
		// Arrange
		ShoppingItem item = new ShoppingItem("apples", 2);
		SearchResult searchResult = new SearchResult("sku123", "Red Apples", "Brand", BigDecimal.valueOf(1.99), "url",
				"Fruit");

		when(shoppingListProvider.getShoppingList("list.json")).thenReturn(List.of(item));
		when(credentialProvider.getUsername("CONTINENTE")).thenReturn("user");
		when(credentialProvider.getPassword("CONTINENTE")).thenReturn("pass");
		when(productService.findMappingsBySearchTextAndSupermarket("apples", "CONTINENTE")).thenReturn(List.of());
		when(supermarketDriver.searchProduct("apples")).thenReturn(new ArrayList<>(List.of(searchResult)));
		when(supermarketDriver.addProductToCart("sku123", 2)).thenReturn(false);

		// Act
		service.startAutoBuy("list.json", "CONTINENTE", false);
		awaitState(AutoBuyState.AWAITING_MAPPING);

		// Assert
		assertThrows(com.autobuy.exception.AutoBuyException.class,
				() -> productResolutionService.resolveMapping("sku123", false));
	}

	@Test
	void resolveMapping_cartAddFailureWithSaveMapping_returnsStatusMessage() {
		// Arrange
		ShoppingItem item = new ShoppingItem("apples", 2);
		SearchResult searchResult = new SearchResult("sku123", "Red Apples", "Brand", BigDecimal.valueOf(1.99), "url",
				"Fruit");

		when(shoppingListProvider.getShoppingList("list.json")).thenReturn(List.of(item));
		when(credentialProvider.getUsername("CONTINENTE")).thenReturn("user");
		when(credentialProvider.getPassword("CONTINENTE")).thenReturn("pass");
		when(productService.findMappingsBySearchTextAndSupermarket("apples", "CONTINENTE")).thenReturn(List.of());
		when(supermarketDriver.searchProduct("apples")).thenReturn(new ArrayList<>(List.of(searchResult)));
		when(supermarketDriver.addProductToCart("sku123", 2)).thenReturn(false);

		// Act
		service.startAutoBuy("list.json", "CONTINENTE", false);
		awaitState(AutoBuyState.AWAITING_MAPPING);

		var status = productResolutionService.resolveMapping("sku123", true);

		// Assert
		assertFalse(status.added());
		assertEquals("Saved as mapping, but out of stock. Please select a fallback alternative.", status.message());
	}

	@Test
	void resolveMapping_skuNotFoundInResults_throwsIllegalArgumentException() {
		// Arrange
		ShoppingItem item = new ShoppingItem("apples", 2);
		SearchResult searchResult = new SearchResult("sku123", "Red Apples", "Brand", BigDecimal.valueOf(1.99), "url",
				"Fruit");

		when(shoppingListProvider.getShoppingList("list.json")).thenReturn(List.of(item));
		when(credentialProvider.getUsername("CONTINENTE")).thenReturn("user");
		when(credentialProvider.getPassword("CONTINENTE")).thenReturn("pass");
		when(productService.findMappingsBySearchTextAndSupermarket("apples", "CONTINENTE")).thenReturn(List.of());
		when(supermarketDriver.searchProduct("apples")).thenReturn(new ArrayList<>(List.of(searchResult)));

		// Act
		service.startAutoBuy("list.json", "CONTINENTE", false);
		awaitState(AutoBuyState.AWAITING_MAPPING);

		// Assert
		assertThrows(IllegalArgumentException.class,
				() -> productResolutionService.resolveMapping("nonexistent-sku", false));
	}

	@Test
	void resolveMapping_exhaustedItemCartAddFailure_skipsItemAndCompletes() {
		// Arrange
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

		// Act
		service.startAutoBuy("list.json", "CONTINENTE", false);
		awaitState(AutoBuyState.AWAITING_EXHAUSTED_RESOLUTIONS);

		productResolutionService.resolveMapping("sku456", false);
		awaitState(AutoBuyState.AWAITING_FINAL_REVIEW);

		// Assert
		assertEquals(1, executionContext.getSkippedItems().size());
		assertEquals("apples", executionContext.getSkippedItems().get(0));

		// Act
		service.completeRun();

		// Assert
		awaitState(AutoBuyState.SUCCESS);
	}

	@Test
	void shutdown_withActiveDriver_closesDriverAndClearsContext() {
		// Arrange
		executionContext.setActiveDriver(supermarketDriver);

		// Act
		service.shutdown();

		// Assert
		verify(supermarketDriver).close();
		assertNull(executionContext.getActiveDriver());
	}

	@Test
	void shutdown_driverCloseThrowsException_clearsActiveDriver() {
		// Arrange
		doThrow(new RuntimeException("close failed")).when(supermarketDriver).close();
		executionContext.setActiveDriver(supermarketDriver);

		// Act
		service.shutdown();

		// Assert
		verify(supermarketDriver).close();
		assertNull(executionContext.getActiveDriver());
	}

	@Test
	void completeRun_keepBrowserTrue_completesWithoutClosingBrowser() {
		// Arrange
		ShoppingItem item = new ShoppingItem("apples", 2);
		SearchResult searchResult = new SearchResult("sku123", "Red Apples", "Brand", BigDecimal.valueOf(1.99), "url",
				"Fruit");

		when(shoppingListProvider.getShoppingList("list.json")).thenReturn(List.of(item));
		when(credentialProvider.getUsername("CONTINENTE")).thenReturn("user");
		when(credentialProvider.getPassword("CONTINENTE")).thenReturn("pass");
		when(productService.findMappingsBySearchTextAndSupermarket("apples", "CONTINENTE")).thenReturn(List.of());
		when(supermarketDriver.searchProduct("apples")).thenReturn(new ArrayList<>(List.of(searchResult)));
		when(supermarketDriver.addProductToCart("sku123", 2)).thenReturn(true);

		// Act
		service.startAutoBuy("list.json", "CONTINENTE", false);

		awaitState(AutoBuyState.AWAITING_MAPPING);
		productResolutionService.resolveMapping("sku123", false);

		awaitState(AutoBuyState.AWAITING_FINAL_REVIEW);

		service.completeRun(true);

		// Assert
		awaitState(AutoBuyState.SUCCESS);
	}

	@Test
	void startAutoBuy_threadInterrupted_transitionsToFailedState() throws InterruptedException {
		// Arrange
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

		// Act
		service.startAutoBuy("list.json", "CONTINENTE", false);

		// Assert
		awaitState(AutoBuyState.FAILED);
		assertTrue(executionContext.getErrorMsg().contains("Execution interrupted"));
	}

	@Test
	void cancel_duringExhaustedResolutions_transitionsToFailedState() {
		// Arrange
		ShoppingItem item = new ShoppingItem("apples", 2);
		ProductMapping mapping = new ProductMapping("apples", "CONTINENTE", "sku123", "Red Apples");

		when(shoppingListProvider.getShoppingList("list.json")).thenReturn(List.of(item));
		when(credentialProvider.getUsername("CONTINENTE")).thenReturn("user");
		when(credentialProvider.getPassword("CONTINENTE")).thenReturn("pass");
		when(productService.findMappingsBySearchTextAndSupermarket("apples", "CONTINENTE"))
				.thenReturn(List.of(mapping));
		when(supermarketDriver.searchProduct("sku123")).thenReturn(List.of());
		when(supermarketDriver.searchProduct("apples")).thenReturn(List.of());

		// Act
		service.startAutoBuy("list.json", "CONTINENTE", false);

		awaitState(AutoBuyState.AWAITING_EXHAUSTED_RESOLUTIONS);

		service.cancel();

		// Assert
		awaitState(AutoBuyState.FAILED);
		assertTrue(executionContext.getErrorMsg().contains("canceled by user"));
	}

	@Test
	void startAutoBuy_stateFailedDuringLoop_stopsProcessingRemainingItems() throws InterruptedException {
		// Arrange
		ShoppingItem item1 = new ShoppingItem("apples", 1);
		ShoppingItem item2 = new ShoppingItem("bananas", 2);

		when(shoppingListProvider.getShoppingList("list.json")).thenReturn(List.of(item1, item2));
		when(credentialProvider.getUsername("CONTINENTE")).thenReturn("user");
		when(credentialProvider.getPassword("CONTINENTE")).thenReturn("pass");

		doAnswer(invocation -> {
			executionContext.transitionTo(AutoBuyState.FAILED);
			return null;
		}).when(productResolutionService).resolveProduct(any(), eq(item1), anyString());

		// Act
		service.startAutoBuy("list.json", "CONTINENTE", false);

		// Assert
		awaitState(AutoBuyState.FAILED);
		verify(productResolutionService, never()).resolveProduct(any(), eq(item2), anyString());
	}

	@Test
	void startAutoBuy_alreadyAwaitingFinalReview_resetsAndRestarts() {
		// Arrange
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

		// Act
		service.startAutoBuy("list.json", "CONTINENTE", false);

		// Assert
		awaitState(AutoBuyState.AWAITING_FINAL_REVIEW);
	}
}
