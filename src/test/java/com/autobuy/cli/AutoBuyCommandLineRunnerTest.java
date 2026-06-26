package com.autobuy.cli;

import com.autobuy.driver.SupermarketDriver;
import com.autobuy.model.*;
import com.autobuy.provider.CredentialProvider;
import com.autobuy.provider.ShoppingListProvider;
import com.autobuy.repository.PriceHistoryRepository;
import com.autobuy.repository.ProductMappingRepository;
import com.autobuy.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class AutoBuyCommandLineRunnerTest {

	private InputStream originalIn;

	@BeforeEach
	void setUp() {
		originalIn = System.in;
		System.setIn(new ByteArrayInputStream("\n\n\n\n\n\n\n\n\n".getBytes()));
	}

	@AfterEach
	void tearDown() {
		System.setIn(originalIn);
	}

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private ProductMappingRepository productMappingRepository;

	@Autowired
	private PriceHistoryRepository priceHistoryRepository;

	// Stub classes to avoid bytecode generation issues on Java 25
	private static class StubCredentialProvider implements CredentialProvider {
		@Override
		public String getUsername(String supermarket) {
			return "test-user@example.com";
		}

		@Override
		public String getPassword(String supermarket) {
			return "test-pass";
		}
	}

	private static class StubShoppingListProvider implements ShoppingListProvider {
		private final List<ShoppingItem> items;

		public StubShoppingListProvider(List<ShoppingItem> items) {
			this.items = items;
		}

		@Override
		public List<ShoppingItem> getShoppingList(String sourcePath) {
			return items;
		}
	}

	private static class StubSupermarketDriver implements SupermarketDriver {
		private final String name;
		private final List<SearchResult> searchResultsToReturn = new ArrayList<>();
		private final List<String> addedSkus = new ArrayList<>();
		private boolean initialized = false;
		private boolean closed = false;

		public StubSupermarketDriver(String name) {
			this.name = name;
		}

		public void addSearchResult(SearchResult result) {
			this.searchResultsToReturn.add(result);
		}

		public List<String> getAddedSkus() {
			return addedSkus;
		}

		public boolean isInitialized() {
			return initialized;
		}

		public boolean isClosed() {
			return closed;
		}

		@Override
		public String getSupermarketName() {
			return name;
		}

		@Override
		public void initialize(String username, String password, boolean headful) {
			this.initialized = true;
		}

		@Override
		public List<SearchResult> searchProduct(String query) {
			// In our test, if we query for a SKU or matching search query, return the
			// matched items
			return searchResultsToReturn.stream()
					.filter(r -> r.externalId().equals(query) || r.name().toLowerCase().contains(query.toLowerCase()))
					.toList();
		}

		@Override
		public boolean addProductToCart(String externalId, int quantity) {
			for (int i = 0; i < quantity; i++) {
				addedSkus.add(externalId);
			}
			return true;
		}

		@Override
		public void close() {
			this.closed = true;
		}
	}

	@Test
	void testRunWithEmptyShoppingList() {
		// Arrange
		StubCredentialProvider credentialProvider = new StubCredentialProvider();
		StubShoppingListProvider shoppingListProvider = new StubShoppingListProvider(Collections.emptyList());
		StubSupermarketDriver driver = new StubSupermarketDriver("CONTINENTE");

		AutoBuyCommandLineRunner runner = new AutoBuyCommandLineRunner(productRepository, productMappingRepository,
				priceHistoryRepository, List.of(driver), credentialProvider, shoppingListProvider);

		// Act & Assert
		assertDoesNotThrow(() -> runner.run());
		assertFalse(driver.isInitialized());
	}

	@Test
	void testRunWithUnsupportedSupermarket() {
		// Arrange
		StubCredentialProvider credentialProvider = new StubCredentialProvider();
		StubShoppingListProvider shoppingListProvider = new StubShoppingListProvider(
				List.of(new ShoppingItem("Milk", 2)));

		// No driver registered for CONTINENTE (only ALDI)
		StubSupermarketDriver driver = new StubSupermarketDriver("ALDI");

		AutoBuyCommandLineRunner runner = new AutoBuyCommandLineRunner(productRepository, productMappingRepository,
				priceHistoryRepository, List.of(driver), credentialProvider, shoppingListProvider);

		// Act & Assert
		assertDoesNotThrow(() -> runner.run("--supermarket=CONTINENTE"));
		assertFalse(driver.isInitialized());
	}

	@Test
	void testRunWithExistingMapping() {
		// Arrange
		// 1. Save mapped product and query mapping in DB
		ProductMapping mapping = new ProductMapping("mimosa leite", "CONTINENTE", "SKU123", "Mimosa Milk");
		productMappingRepository.save(mapping);

		Product product = new Product("SKU123", "CONTINENTE", "Mimosa Milk", "Mimosa", "http://test", "Milk");
		productRepository.save(product);

		// 2. Set up providers and driver
		StubCredentialProvider credentialProvider = new StubCredentialProvider();
		StubShoppingListProvider shoppingListProvider = new StubShoppingListProvider(
				List.of(new ShoppingItem("mimosa leite", 3)));

		StubSupermarketDriver driver = new StubSupermarketDriver("CONTINENTE");
		SearchResult result = new SearchResult("SKU123", "Mimosa Milk", "Mimosa", new BigDecimal("1.25"), "http://test",
				"Milk");
		driver.addSearchResult(result);

		AutoBuyCommandLineRunner runner = new AutoBuyCommandLineRunner(productRepository, productMappingRepository,
				priceHistoryRepository, List.of(driver), credentialProvider, shoppingListProvider);

		// Act
		// We pass --headless to avoid any user prompt block
		assertDoesNotThrow(() -> runner.run("--headless"));

		// Assert
		assertTrue(driver.isInitialized());
		assertEquals(3, driver.getAddedSkus().size());
		assertEquals("SKU123", driver.getAddedSkus().get(0));

		// Verify price history is correctly logged
		List<PriceHistory> history = priceHistoryRepository.findAll();
		assertEquals(1, history.size());
		assertEquals(new BigDecimal("1.25"), history.get(0).getPrice());
		assertEquals("SKU123", history.get(0).getProduct().getExternalId());
	}

	@Test
	void testRunWithLoginFailure() {
		// Arrange
		StubCredentialProvider credentialProvider = new StubCredentialProvider();
		StubShoppingListProvider shoppingListProvider = new StubShoppingListProvider(
				List.of(new ShoppingItem("Milk", 2)));

		// Driver throws exception on initialize
		StubSupermarketDriver driver = new StubSupermarketDriver("CONTINENTE") {
			@Override
			public void initialize(String username, String password, boolean headful) {
				throw new RuntimeException("Login timed out");
			}
		};

		AutoBuyCommandLineRunner runner = new AutoBuyCommandLineRunner(productRepository, productMappingRepository,
				priceHistoryRepository, List.of(driver), credentialProvider, shoppingListProvider);

		// Act & Assert
		assertDoesNotThrow(() -> runner.run());
		assertTrue(driver.isClosed()); // Ensure driver is closed on failure
	}
}
