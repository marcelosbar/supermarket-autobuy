package com.autobuy.web;

import com.autobuy.driver.SupermarketDriver;
import com.autobuy.model.*;
import com.autobuy.provider.CredentialProvider;
import com.autobuy.provider.ShoppingListProvider;
import com.autobuy.service.PriceHistoryService;
import com.autobuy.service.ProductService;
import com.autobuy.web.dto.AutoBuyStatusResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Service to orchestrate the Auto-Buy process in the background for the Web UI.
 * Handles state, thread synchronization for manual mapping prompts, and
 * logging.
 */
@Service
public class AutoBuyWebService {

	private static final Logger log = LoggerFactory.getLogger(AutoBuyWebService.class);

	private final ProductService productService;
	private final PriceHistoryService priceHistoryService;
	private final List<SupermarketDriver> drivers;
	private final CredentialProvider credentialProvider;
	private final ShoppingListProvider shoppingListProvider;

	// State fields
	private AutoBuyState state = AutoBuyState.IDLE;
	private String currentItemQuery = "";
	private int currentItemQuantity = 0;
	private List<SearchResult> searchResults = new ArrayList<>();
	private String errorMsg = "";

	// Execution synchronization
	private final Object lock = new Object();
	private boolean resolved = false;
	private SearchResult userSelectedProduct = null;
	private Thread executionThread = null;
	private SupermarketDriver activeDriver = null;

	public AutoBuyWebService(ProductService productService, PriceHistoryService priceHistoryService,
			List<SupermarketDriver> drivers, CredentialProvider credentialProvider,
			ShoppingListProvider shoppingListProvider) {
		this.productService = productService;
		this.priceHistoryService = priceHistoryService;
		this.drivers = drivers;
		this.credentialProvider = credentialProvider;
		this.shoppingListProvider = shoppingListProvider;
	}

	public enum AutoBuyState {
		IDLE, RUNNING, AWAITING_MAPPING, AWAITING_FINAL_REVIEW, SUCCESS, FAILED
	}

	/**
	 * Gets the current status of the auto-buy thread.
	 */
	public synchronized AutoBuyStatusResponse getStatus() {
		return new AutoBuyStatusResponse(state, currentItemQuery, currentItemQuantity, new ArrayList<>(searchResults),
				new ArrayList<>(MemoryAppender.getLogs()), errorMsg);
	}

	/**
	 * Starts the background auto-buy execution run.
	 */
	public synchronized void startAutoBuy(String listPath, String targetSupermarket, boolean headless) {
		if (state == AutoBuyState.RUNNING || state == AutoBuyState.AWAITING_MAPPING
				|| state == AutoBuyState.AWAITING_FINAL_REVIEW) {
			throw new IllegalStateException("An auto-buy execution is already in progress.");
		}

		this.state = AutoBuyState.RUNNING;
		this.errorMsg = "";
		this.currentItemQuery = "";
		this.currentItemQuantity = 0;
		this.searchResults.clear();
		MemoryAppender.clear();

		log.info("Starting background auto-buy run for {}...", targetSupermarket);

		executionThread = new Thread(() -> runExecutionFlow(listPath, targetSupermarket, headless), "AutoBuy-Executor");
		executionThread.start();
	}

	/**
	 * Resolves a missing mapping by specifying a chosen search result.
	 */
	public void resolveMapping(String externalId) {
		synchronized (lock) {
			if (state != AutoBuyState.AWAITING_MAPPING) {
				throw new IllegalStateException("Not currently waiting for product mapping resolution.");
			}

			if (externalId == null || externalId.isBlank() || "skip".equalsIgnoreCase(externalId.trim())) {
				this.userSelectedProduct = null;
				this.resolved = true;
				this.state = AutoBuyState.RUNNING;
				lock.notifyAll();
				return;
			}

			SearchResult selected = searchResults.stream().filter(r -> r.externalId().equals(externalId)).findFirst()
					.orElse(null);

			if (selected == null) {
				throw new IllegalArgumentException("Selected externalId not found in current search results.");
			}

			this.userSelectedProduct = selected;
			this.resolved = true;
			this.state = AutoBuyState.RUNNING;
			lock.notifyAll();
		}
	}

	/**
	 * Completes the execution, closing the driver window.
	 */
	public void completeRun() {
		synchronized (lock) {
			if (state != AutoBuyState.AWAITING_FINAL_REVIEW) {
				throw new IllegalStateException("Not currently waiting for final review.");
			}
			this.resolved = true;
			this.state = AutoBuyState.SUCCESS;
			lock.notifyAll();
		}
	}

	/**
	 * Cancels the currently running execution.
	 */
	public synchronized void cancel() {
		log.warn("Auto-buy run canceled by user.");
		if (activeDriver != null) {
			try {
				activeDriver.close();
			} catch (Exception e) {
				log.error("Error closing driver on cancel", e);
			}
			activeDriver = null;
		}

		synchronized (lock) {
			this.state = AutoBuyState.FAILED;
			this.errorMsg = "Execution canceled by user.";
			this.resolved = true;
			lock.notifyAll();
		}

		if (executionThread != null && executionThread.isAlive()) {
			executionThread.interrupt();
		}
	}

	private void runExecutionFlow(String listPath, String targetSupermarket, boolean headless) {
		// 1. Select Driver
		SupermarketDriver driver = drivers.stream()
				.filter(d -> d.getSupermarketName().equalsIgnoreCase(targetSupermarket)).findFirst().orElse(null);

		if (driver == null) {
			updateStateFailure("No driver found for supermarket: " + targetSupermarket);
			return;
		}

		activeDriver = driver;

		// 2. Load Shopping List
		List<ShoppingItem> shoppingList = shoppingListProvider.getShoppingList(listPath);
		if (shoppingList.isEmpty()) {
			updateStateFailure("Shopping list is empty or file not found: " + listPath);
			activeDriver = null;
			return;
		}

		// 3. Resolve Credentials
		String username = credentialProvider.getUsername(targetSupermarket);
		String password = credentialProvider.getPassword(targetSupermarket);

		if (username == null || username.isBlank() || password == null || password.isBlank()) {
			updateStateFailure("Credentials for " + targetSupermarket
					+ " are not configured. Please save credentials in the Web UI first.");
			activeDriver = null;
			return;
		}

		// 4. Initialize Driver & Log In
		try {
			log.info("Initializing supermarket driver for {}...", targetSupermarket);
			driver.initialize(username, password, !headless);
		} catch (Exception e) {
			log.error("Failed to initialize driver", e);
			updateStateFailure("Failed to initialize driver: " + e.getMessage());
			try {
				driver.close();
			} catch (Exception ignored) {
			}
			activeDriver = null;
			return;
		}

		// 5. Process Items
		try {
			for (ShoppingItem item : shoppingList) {
				if (Thread.currentThread().isInterrupted() || state == AutoBuyState.FAILED) {
					break;
				}

				synchronized (this) {
					this.currentItemQuery = item.query();
					this.currentItemQuantity = item.quantity();
				}

				log.info("Processing item: '{}' (Quantity: {})", item.query(), item.quantity());

				Optional<ProductMapping> mappingOpt = productService
						.findMappingBySearchTextAndSupermarket(item.query().toLowerCase().trim(), targetSupermarket);

				SearchResult selectedProduct = null;

				if (mappingOpt.isPresent()) {
					ProductMapping mapping = mappingOpt.get();
					log.info("Found mapping in DB for '{}' -> SKU: {}", item.query(), mapping.getExternalProductId());

					// Search SKU to get current price/details
					List<SearchResult> skuResults = driver.searchProduct(mapping.getExternalProductId());
					if (!skuResults.isEmpty()) {
						selectedProduct = skuResults.get(0);
					} else {
						log.warn("Product SKU {} not found on search. Performing generic search...",
								mapping.getExternalProductId());
					}
				}

				if (selectedProduct == null) {
					log.info("No mapping found for query '{}'. Performing store search...", item.query());
					List<SearchResult> searchResultsList = driver.searchProduct(item.query());
					if (searchResultsList.isEmpty()) {
						log.warn("No products found for query '{}'. Skipping.", item.query());
						continue;
					}

					// Pause and wait for Web UI selection
					selectedProduct = pauseAndResolveMapping(item.query(), item.quantity(), searchResultsList);
					if (selectedProduct == null) {
						log.info("Skipped item '{}' on user mapping prompt.", item.query());
						continue;
					}

					// Save new mapping and product details
					saveMapping(item.query().toLowerCase().trim(), targetSupermarket, selectedProduct);
				}

				// Log Price and Add to Cart
				if (selectedProduct != null) {
					logPrice(selectedProduct, targetSupermarket);
					boolean success = driver.addProductToCart(selectedProduct.externalId(), item.quantity());
					if (success) {
						log.info("SUCCESS: Added {}x '{}' to cart.", item.quantity(), selectedProduct.name());
					} else {
						log.error("ERROR: Failed to add '{}' to cart.", selectedProduct.name());
					}
				}
			}

			// 6. Complete Automation and hold session for manual review
			if (state != AutoBuyState.FAILED) {
				log.info("All items processed successfully!");
				log.info("Awaiting final cart review in browser window...");
				pauseForFinalReview();
			}

		} catch (InterruptedException e) {
			log.warn("Auto-buy thread interrupted.");
			synchronized (this) {
				if (state == AutoBuyState.RUNNING || state == AutoBuyState.AWAITING_MAPPING) {
					state = AutoBuyState.FAILED;
					errorMsg = "Execution interrupted.";
				}
			}
		} catch (Exception e) {
			log.error("Unexpected error in background auto-buy run", e);
			updateStateFailure("Unexpected execution error: " + e.getMessage());
		} finally {
			try {
				driver.close();
			} catch (Exception e) {
				log.error("Error closing driver", e);
			}
			activeDriver = null;
			synchronized (this) {
				if (state == AutoBuyState.RUNNING || state == AutoBuyState.AWAITING_FINAL_REVIEW) {
					state = AutoBuyState.SUCCESS;
				}
			}
			log.info("Auto-buy background thread completed.");
		}
	}

	private SearchResult pauseAndResolveMapping(String query, int quantity, List<SearchResult> results)
			throws InterruptedException {
		synchronized (lock) {
			this.searchResults = results;
			this.state = AutoBuyState.AWAITING_MAPPING;
			this.resolved = false;
			this.userSelectedProduct = null;

			log.info("PAUSED: Awaiting product mapping resolution from the Web UI for '{}'...", query);

			while (!resolved && state == AutoBuyState.AWAITING_MAPPING) {
				lock.wait();
			}

			this.searchResults.clear();
			return userSelectedProduct;
		}
	}

	private void pauseForFinalReview() throws InterruptedException {
		synchronized (lock) {
			this.state = AutoBuyState.AWAITING_FINAL_REVIEW;
			this.resolved = false;

			while (!resolved && state == AutoBuyState.AWAITING_FINAL_REVIEW) {
				lock.wait();
			}
		}
	}

	private synchronized void updateStateFailure(String message) {
		this.state = AutoBuyState.FAILED;
		this.errorMsg = message;
		log.error("Auto-buy execution failed: {}", message);
	}

	private void saveMapping(String query, String supermarket, SearchResult result) {
		try {
			productService.findOrCreateProduct(result.externalId(), supermarket, result.name(), result.brand(),
					result.url(), result.category());

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

			priceHistoryService.logPrice(product, result.price(), LocalDateTime.now(ZoneId.systemDefault()));
			log.info("Logged price for {}: {} €", product.getName(), result.price());
		} catch (Exception e) {
			log.error("Failed to log price history: {}", e.getMessage());
		}
	}
}
