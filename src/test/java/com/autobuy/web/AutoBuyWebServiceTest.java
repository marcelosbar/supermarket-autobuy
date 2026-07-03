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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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

		service = new AutoBuyWebService(productService, priceHistoryService, List.of(supermarketDriver),
				credentialProvider, shoppingListProvider, taskExecutor);
	}

	@AfterEach
	void tearDown() {
		taskExecutor.shutdown();
	}

	private void awaitState(AutoBuyWebService.AutoBuyState targetState) {
		long start = System.currentTimeMillis();
		while (System.currentTimeMillis() - start < 3000) {
			if (service.getStatus().state() == targetState) {
				return;
			}
			try {
				Thread.sleep(10);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				fail("Interrupted waiting for state " + targetState);
			}
		}
		fail("Timeout waiting for state " + targetState + ". Current state: " + service.getStatus().state());
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
		verify(supermarketDriver).close();
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
		verify(supermarketDriver).close();
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
		verify(productService).saveMapping(eq("apples"), eq("CONTINENTE"), eq(searchResult2));

		// Complete
		service.completeRun();
		awaitState(AutoBuyWebService.AutoBuyState.SUCCESS);
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
		verify(supermarketDriver).close();
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
}
