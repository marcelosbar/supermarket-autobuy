package com.autobuy.cli;

import com.autobuy.driver.SupermarketDriver;
import com.autobuy.model.*;
import com.autobuy.provider.CredentialProvider;
import com.autobuy.provider.ShoppingListProvider;
import com.autobuy.service.PriceHistoryService;
import com.autobuy.service.ProductService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;

/**
 * CLI Orchestrator for the Supermarket Auto-Buy tool.
 */
@Component
public class AutoBuyCommandLineRunner implements org.springframework.boot.CommandLineRunner {

	private static final Logger log = LoggerFactory.getLogger(AutoBuyCommandLineRunner.class);

	private final ProductService productService;
	private final PriceHistoryService priceHistoryService;
	private final List<SupermarketDriver> drivers;
	private final CredentialProvider credentialProvider;
	private final ShoppingListProvider shoppingListProvider;

	public AutoBuyCommandLineRunner(ProductService productService, PriceHistoryService priceHistoryService,
			List<SupermarketDriver> drivers, CredentialProvider credentialProvider,
			ShoppingListProvider shoppingListProvider) {
		this.productService = productService;
		this.priceHistoryService = priceHistoryService;
		this.drivers = drivers;
		this.credentialProvider = credentialProvider;
		this.shoppingListProvider = shoppingListProvider;
	}

	public void run(String... args) {
		boolean isCliMode = false;
		for (String arg : args) {
			if (arg.equalsIgnoreCase("--cli")) {
				isCliMode = true;
				break;
			}
		}

		if (!isCliMode) {
			log.info("CLI mode not requested (--cli flag not present). Skipping CLI execution.");
			return;
		}

		log.info("Starting Supermarket Auto-Buy execution...");

		// 1. Parse Arguments
		String listPath = "shopping-list.json";
		String targetSupermarket = "CONTINENTE";
		boolean headless = false;

		for (String arg : args) {
			if (arg.startsWith("--list=")) {
				listPath = arg.substring(7);
			} else if (arg.startsWith("--supermarket=")) {
				targetSupermarket = arg.substring(14).toUpperCase();
			} else if (arg.equals("--headless")) {
				headless = true;
			}
		}

		log.info("Configured list: {}, Supermarket: {}, Headless: {}", listPath, targetSupermarket, headless);

		// 2. Select Driver
		final String supermarketName = targetSupermarket;
		SupermarketDriver driver = drivers.stream()
				.filter(d -> d.getSupermarketName().equalsIgnoreCase(supermarketName)).findFirst().orElse(null);

		if (driver == null) {
			log.error("No driver found for supermarket: {}", targetSupermarket);
			return;
		}

		// 3. Load Shopping List
		List<ShoppingItem> shoppingList = shoppingListProvider.getShoppingList(listPath);
		if (shoppingList.isEmpty()) {
			log.warn("Shopping list is empty. Exiting.");
			return;
		}

		// 4. Resolve Credentials (Hybrid approach)
		Scanner scanner = new Scanner(System.in);
		String username = credentialProvider.getUsername(targetSupermarket);
		String password = credentialProvider.getPassword(targetSupermarket);

		if (username == null || username.isBlank()) {
			System.out.print("Enter " + targetSupermarket + " username (email): ");
			username = scanner.nextLine().trim();
		}

		if (password == null || password.isBlank()) {
			System.out.print("Enter " + targetSupermarket + " password: ");
			if (System.console() != null) {
				password = new String(System.console().readPassword());
			} else {
				password = scanner.nextLine().trim();
			}
		}

		// 5. Initialize Driver & Log In
		try {
			driver.initialize(username, password, !headless);
		} catch (Exception e) {
			log.error("Failed to initialize supermarket driver: {}", e.getMessage());
			driver.close();
			return;
		}

		// 6. Process items
		System.out.println("\n==============================================");
		System.out.println("Processing shopping list of " + shoppingList.size() + " items...");
		System.out.println("==============================================\n");

		for (ShoppingItem item : shoppingList) {
			log.info("Processing item: '{}' (Quantity: {})", item.query(), item.quantity());

			Optional<ProductMapping> mappingOpt = productService
					.findMappingBySearchTextAndSupermarket(item.query().toLowerCase().trim(), targetSupermarket);

			SearchResult selectedProduct = null;

			if (mappingOpt.isPresent()) {
				ProductMapping mapping = mappingOpt.get();
				log.info("Found mapping in DB for '{}' -> SKU: {}", item.query(), mapping.getExternalProductId());

				// Search for the SKU to get the current price and details
				List<SearchResult> skuResults = driver.searchProduct(mapping.getExternalProductId());
				if (!skuResults.isEmpty()) {
					selectedProduct = skuResults.get(0);
				} else {
					log.warn(
							"Product SKU {} not found on search. Maybe out of stock or URL changed. Will perform generic search...",
							mapping.getExternalProductId());
				}
			}

			// If no mapping, or if mapping product search failed, execute a generic search
			if (selectedProduct == null) {
				if (headless) {
					log.warn("Running in headless mode; cannot prompt interactively for unmatched item '{}'. Skipping.",
							item.query());
					continue;
				}

				List<SearchResult> searchResults = driver.searchProduct(item.query());
				if (searchResults.isEmpty()) {
					log.warn("No products found for query '{}'. Skipping.", item.query());
					continue;
				}

				// Interactive prompt
				selectedProduct = promptUserToChooseProduct(scanner, item.query(), searchResults);
				if (selectedProduct == null) {
					log.info("Skipped item '{}'.", item.query());
					continue;
				}

				// Save new mapping and product details to DB
				saveMapping(item.query().toLowerCase().trim(), targetSupermarket, selectedProduct);
			}

			// Log Price History and Add to Cart
			if (selectedProduct != null) {
				logPrice(selectedProduct, targetSupermarket);
				boolean success = driver.addProductToCart(selectedProduct.externalId(), item.quantity());
				if (success) {
					System.out.printf("SUCCESS: Added %dx '%s' to cart.%n%n", item.quantity(), selectedProduct.name());
				} else {
					System.out.printf("ERROR: Failed to add '%s' to cart.%n%n", selectedProduct.name());
				}
			}
		}

		// 7. Complete Automation and hold session
		System.out.println("==============================================");
		System.out.println("All items processed!");
		System.out.println("Please review your shopping cart in the browser window.");
		System.out.println("Press ENTER in the terminal to close the browser and exit...");
		System.out.println("==============================================");

		scanner.nextLine();

		// 8. Shutdown Driver (H2 backup is triggered automatically on shutdown via
		// @PreDestroy)
		driver.close();
		log.info("Auto-buy application execution completed successfully.");
	}

	private SearchResult promptUserToChooseProduct(Scanner scanner, String query, List<SearchResult> results) {
		System.out.println("\n----------------------------------------------");
		System.out.printf("No mapping found for search query: '%s'%n", query);
		System.out.println("Please choose the correct product from the search results below:");

		for (int i = 0; i < results.size(); i++) {
			SearchResult res = results.get(i);
			System.out.printf("[%d] %s (Brand: %s) - Price: %s €%n", i + 1, res.name(), res.brand(), res.price());
		}
		System.out.println("[s] Skip this item");
		System.out.println("----------------------------------------------");

		while (true) {
			System.out.print("Select index: ");
			String input = scanner.nextLine().trim().toLowerCase();

			if (input.equals("s")) {
				return null;
			}

			try {
				int index = Integer.parseInt(input) - 1;
				if (index >= 0 && index < results.size()) {
					return results.get(index);
				}
			} catch (NumberFormatException ignored) {
			}

			System.out.println(
					"Invalid selection. Please enter a number between 1 and " + results.size() + ", or 's' to skip.");
		}
	}

	private void saveMapping(String query, String supermarket, SearchResult result) {
		try {
			Product product = productService.findOrCreateProduct(result.externalId(), supermarket, result.name(),
					result.brand(), result.url(), result.category());

			ProductMapping mapping = new ProductMapping(query, supermarket, result.externalId(), result.name());
			productService.saveMapping(mapping);
			log.info("Saved product mapping: '{}' -> SKU: {}", query, result.externalId());
		} catch (Exception e) {
			log.error("Failed to save product mapping: {}", e.getMessage());
		}
	}

	private void logPrice(SearchResult result, String supermarket) {
		try {
			Product product = productService.findOrCreateProduct(result.externalId(), supermarket, result.name(),
					result.brand(), result.url(), result.category());

			priceHistoryService.logPrice(product, result.price(), LocalDateTime.now());
			log.info("Logged price for {}: {} €", product.getName(), result.price());
		} catch (Exception e) {
			log.error("Failed to log price history: {}", e.getMessage());
		}
	}
}
