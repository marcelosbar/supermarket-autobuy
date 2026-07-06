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
import org.springframework.core.task.AsyncTaskExecutor;

import java.util.concurrent.Future;
import java.util.concurrent.CompletableFuture;
import java.util.ArrayList;
import java.util.List;

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

	private final AsyncTaskExecutor taskExecutor;

	// State fields
	private AutoBuyState state = AutoBuyState.IDLE;
	private String currentItemQuery = "";
	private int currentItemQuantity = 0;
	private List<SearchResult> searchResults = new ArrayList<>();
	private String errorMsg = "";
	private final List<String> skippedItems = new ArrayList<>();
	private final List<ShoppingItem> exhaustedItems = new ArrayList<>();

	// Execution synchronization
	private SupermarketDriver activeDriver = null;
	private Future<?> currentExecutionFuture = null;
	private CompletableFuture<ResolutionAction> currentMappingFuture = null;
	private CompletableFuture<com.autobuy.web.dto.ResolutionResult> currentResolutionFuture = null;
	private CompletableFuture<Void> finalReviewFuture = null;

	public AutoBuyWebService(ProductService productService, PriceHistoryService priceHistoryService,
			List<SupermarketDriver> drivers, CredentialProvider credentialProvider,
			ShoppingListProvider shoppingListProvider, AsyncTaskExecutor autoBuyTaskExecutor) {
		this.productService = productService;
		this.priceHistoryService = priceHistoryService;
		this.drivers = drivers;
		this.credentialProvider = credentialProvider;
		this.shoppingListProvider = shoppingListProvider;
		this.taskExecutor = autoBuyTaskExecutor;
	}

	public enum AutoBuyState {
		IDLE, RUNNING, AWAITING_MAPPING, AWAITING_EXHAUSTED_RESOLUTIONS, AWAITING_FINAL_REVIEW, SUCCESS, FAILED
	}

	/**
	 * Gets the current status of the auto-buy thread.
	 */
	public synchronized AutoBuyStatusResponse getStatus() {
		List<String> exhaustedQueries = exhaustedItems.stream().map(ShoppingItem::query).toList();
		return new AutoBuyStatusResponse(state, currentItemQuery, currentItemQuantity, new ArrayList<>(searchResults),
				new ArrayList<>(MemoryAppender.getLogs()), errorMsg, new ArrayList<>(skippedItems), exhaustedQueries);
	}

	/**
	 * Starts the background auto-buy execution run.
	 */
	public synchronized void startAutoBuy(String listPath, String targetSupermarket, boolean headless) {
		if (state == AutoBuyState.RUNNING || state == AutoBuyState.AWAITING_MAPPING
				|| state == AutoBuyState.AWAITING_EXHAUSTED_RESOLUTIONS
				|| state == AutoBuyState.AWAITING_FINAL_REVIEW) {
			throw new IllegalStateException("An auto-buy execution is already in progress.");
		}

		this.state = AutoBuyState.RUNNING;
		this.errorMsg = "";
		this.currentItemQuery = "";
		this.currentItemQuantity = 0;
		this.searchResults.clear();
		this.skippedItems.clear();
		this.exhaustedItems.clear();
		MemoryAppender.clear();

		log.info("Starting background auto-buy run for {}...", targetSupermarket);

		currentExecutionFuture = taskExecutor.submit(() -> runExecutionFlow(listPath, targetSupermarket, headless));
	}

	/**
	 * Resolves a missing mapping by specifying a chosen search result.
	 */
	public synchronized void resolveMapping(String externalId, boolean saveMapping) {
		if (state != AutoBuyState.AWAITING_MAPPING && state != AutoBuyState.AWAITING_EXHAUSTED_RESOLUTIONS) {
			throw new IllegalStateException("Not currently waiting for product mapping resolution.");
		}

		if (state == AutoBuyState.AWAITING_MAPPING) {
			CompletableFuture<ResolutionAction> future = this.currentMappingFuture;
			if (future == null) {
				return;
			}

			if (externalId == null || externalId.isBlank() || "skip".equalsIgnoreCase(externalId.trim())) {
				this.state = AutoBuyState.RUNNING;
				this.currentMappingFuture = null;
				future.complete(new ResolutionAction(ResolutionAction.ActionType.SKIP, null));
				return;
			}

			SearchResult selected = searchResults.stream().filter(r -> r.externalId().equals(externalId)).findFirst()
					.orElse(null);

			if (selected == null) {
				throw new IllegalArgumentException("Selected externalId not found in current search results.");
			}

			this.state = AutoBuyState.RUNNING;
			this.currentMappingFuture = null;
			future.complete(new ResolutionAction(ResolutionAction.ActionType.SELECT, externalId, saveMapping));
		} else if (state == AutoBuyState.AWAITING_EXHAUSTED_RESOLUTIONS) {
			CompletableFuture<com.autobuy.web.dto.ResolutionResult> future = this.currentResolutionFuture;
			if (future == null) {
				return;
			}

			if (externalId == null || externalId.isBlank() || "skip".equalsIgnoreCase(externalId.trim())) {
				this.state = AutoBuyState.RUNNING;
				this.currentResolutionFuture = null;
				future.complete(null);
				return;
			}

			if (activeDriver != null) {
				boolean available = activeDriver.isProductAvailable(externalId);
				if (!available) {
					throw new IllegalArgumentException(
							"The selected product is out of stock or unavailable. Please choose another.");
				}
			}

			SearchResult selected = searchResults.stream().filter(r -> r.externalId().equals(externalId)).findFirst()
					.orElse(null);

			if (selected == null) {
				throw new IllegalArgumentException("Selected externalId not found in current search results.");
			}

			this.state = AutoBuyState.RUNNING;
			this.currentResolutionFuture = null;
			future.complete(new com.autobuy.web.dto.ResolutionResult(selected, saveMapping));
		}
	}

	/**
	 * Refines the active search by triggering a new search on the driver thread.
	 */
	public synchronized void refineSearch(String newQuery) {
		if (state != AutoBuyState.AWAITING_MAPPING) {
			throw new IllegalStateException("Not currently waiting for product mapping resolution.");
		}

		CompletableFuture<ResolutionAction> future = this.currentMappingFuture;
		if (future == null) {
			return;
		}

		if (newQuery == null || newQuery.isBlank()) {
			throw new IllegalArgumentException("Refined search query cannot be blank.");
		}

		this.state = AutoBuyState.RUNNING;
		this.currentMappingFuture = null;
		future.complete(new ResolutionAction(ResolutionAction.ActionType.REFINE, newQuery));
=======
			future.complete(selected);
		} else if (state == AutoBuyState.AWAITING_EXHAUSTED_RESOLUTIONS) {
			CompletableFuture<com.autobuy.web.dto.ResolutionResult> future = this.currentResolutionFuture;
			if (future == null) {
				return;
			}

			if (externalId == null || externalId.isBlank() || "skip".equalsIgnoreCase(externalId.trim())) {
				this.state = AutoBuyState.RUNNING;
				this.currentResolutionFuture = null;
				future.complete(null);
				return;
			}

			SearchResult selected = searchResults.stream().filter(r -> r.externalId().equals(externalId)).findFirst()
					.orElse(null);

			if (selected == null) {
				throw new IllegalArgumentException("Selected externalId not found in current search results.");
			}

			this.state = AutoBuyState.RUNNING;
			this.currentResolutionFuture = null;
			future.complete(new com.autobuy.web.dto.ResolutionResult(selected, saveMapping));
		}
>>>>>>> 6d66be0 (feat: prioritized alternative products chain and deferred resolutions)
	}

	/**
	 * Completes the execution, closing the driver window.
	 */
	public synchronized void completeRun() {
		if (state != AutoBuyState.AWAITING_FINAL_REVIEW) {
			throw new IllegalStateException("Not currently waiting for final review.");
		}
		this.state = AutoBuyState.SUCCESS;
		CompletableFuture<Void> future = this.finalReviewFuture;
		this.finalReviewFuture = null;
		if (future != null) {
			future.complete(null);
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

		this.state = AutoBuyState.FAILED;
		this.errorMsg = "Execution canceled by user.";

		if (currentMappingFuture != null) {
			currentMappingFuture.cancel(true);
			currentMappingFuture = null;
		}
		if (currentResolutionFuture != null) {
			currentResolutionFuture.cancel(true);
			currentResolutionFuture = null;
		}
		if (finalReviewFuture != null) {
			finalReviewFuture.cancel(true);
			finalReviewFuture = null;
		}
		if (currentExecutionFuture != null) {
			currentExecutionFuture.cancel(true);
			currentExecutionFuture = null;
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
		if (!initializeDriverAndLogin(driver, targetSupermarket, username, password, headless)) {
			return;
		}

		// 5. Process Items
		try {
			processShoppingList(driver, shoppingList, targetSupermarket);
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
				currentExecutionFuture = null;
			}
			log.info("Auto-buy background thread completed.");
		}
	}
	private void processShoppingList(SupermarketDriver driver, List<ShoppingItem> shoppingList,
			String targetSupermarket) {
		try {
			List<ShoppingItem> orderedList = partitionByMappingStatus(shoppingList, targetSupermarket);
			for (ShoppingItem item : orderedList) {
				if (Thread.currentThread().isInterrupted() || state == AutoBuyState.FAILED) {
					break;
				}
				processShoppingItem(driver, item, targetSupermarket);
			}

			// Defer resolutions to the end of the run
			if (state != AutoBuyState.FAILED && !exhaustedItems.isEmpty()) {
				log.info("Awaiting user resolution for {} unavailable/unmapped items...", exhaustedItems.size());
				while (!exhaustedItems.isEmpty() && state != AutoBuyState.FAILED
						&& !Thread.currentThread().isInterrupted()) {
					ShoppingItem failedItem = exhaustedItems.get(0);
					com.autobuy.web.dto.ResolutionResult resolution = pauseAndResolveExhausted(failedItem,
							targetSupermarket, driver);
					if (resolution == null) {
						log.info("Skipped item '{}' on user resolution prompt.", failedItem.query());
						recordSkippedItem(failedItem.query());
					} else {
						SearchResult selected = resolution.product();
						boolean success = driver.addProductToCart(selected.externalId(), failedItem.quantity());
						if (success) {
							log.info("SUCCESS: Added {}x '{}' to cart.", failedItem.quantity(), selected.name());
							try {
								priceHistoryService.logPrice(selected, targetSupermarket);
								log.info("Logged price for {}: {} €", selected.name(), selected.price());
							} catch (Exception e) {
								log.error("Failed to log price history", e);
							}
							if (resolution.saveMapping()) {
								try {
									List<ProductMapping> existing = productService
											.findMappingsBySearchTextAndSupermarket(
													failedItem.query().toLowerCase().trim(), targetSupermarket);
									int nextPriority = existing.stream().mapToInt(ProductMapping::getPriority).max()
											.orElse(-1) + 1;
									productService.saveMappingWithPriority(failedItem.query().toLowerCase().trim(),
											targetSupermarket, selected, nextPriority);
									log.info("Saved product mapping: '{}' -> SKU: {} (Priority: {})",
											failedItem.query(), selected.externalId(), nextPriority);
								} catch (Exception e) {
									log.error("Failed to save product mapping", e);
								}
							}
						} else {
							log.warn("SKIPPED: '{}' is unavailable — not added to cart.", selected.name());
							recordSkippedItem(failedItem.query());
						}
					}
					synchronized (this) {
						exhaustedItems.remove(failedItem);
					}
				}
			}

			// 6. Complete Automation and hold session for manual review
			if (state != AutoBuyState.FAILED) {
				log.info("All items processed successfully!");
				safeNavigateToCart(driver);
				log.info("Awaiting final cart review in browser window...");
				pauseForFinalReview();
			}

		} catch (InterruptedException _) {
			log.warn("Auto-buy thread interrupted.");
			Thread.currentThread().interrupt();
			synchronized (this) {
				if (state == AutoBuyState.RUNNING || state == AutoBuyState.AWAITING_MAPPING
						|| state == AutoBuyState.AWAITING_EXHAUSTED_RESOLUTIONS) {
					state = AutoBuyState.FAILED;
					errorMsg = "Execution interrupted.";
				}
			}
		} catch (Exception e) {
			log.error("Unexpected error in background auto-buy run", e);
			updateStateFailure("Unexpected execution error: " + e.getMessage());
		}
	}

	private void safeNavigateToCart(SupermarketDriver driver) {
		try {
			driver.navigateToCart();
		} catch (Exception e) {
			log.error("Failed to auto-navigate to cart page", e);
		}
	}

	private List<ShoppingItem> partitionByMappingStatus(List<ShoppingItem> items, String supermarket) {
		List<ShoppingItem> unmapped = new ArrayList<>();
		List<ShoppingItem> mapped = new ArrayList<>();
		for (ShoppingItem item : items) {
			boolean hasMappingInDB = !productService
					.findMappingsBySearchTextAndSupermarket(item.query().toLowerCase().trim(), supermarket).isEmpty();
			if (hasMappingInDB) {
				mapped.add(item);
			} else {
				unmapped.add(item);
			}
		}
		if (!unmapped.isEmpty()) {
			log.info("Reordering queue: {} unmapped items will be processed first.", unmapped.size());
		}
		List<ShoppingItem> ordered = new ArrayList<>(unmapped);
		ordered.addAll(mapped);
		return ordered;
	}

	private void processShoppingItem(SupermarketDriver driver, ShoppingItem item, String targetSupermarket)
			throws InterruptedException {
		synchronized (this) {
			this.currentItemQuery = item.query();
			this.currentItemQuantity = item.quantity();
		}

		log.info("Processing item: '{}' (Quantity: {})", item.query(), item.quantity());

		SearchResult selectedProduct = resolveProduct(driver, item, targetSupermarket);
		if (selectedProduct == null) {
			return;
		}

		// Log Price and Add to Cart
		try {
			priceHistoryService.logPrice(selectedProduct, targetSupermarket);
			log.info("Logged price for {}: {} €", selectedProduct.name(), selectedProduct.price());
		} catch (Exception e) {
			log.error("Failed to log price history", e);
		}
		boolean success = driver.addProductToCart(selectedProduct.externalId(), item.quantity());
		if (success) {
			log.info("SUCCESS: Added {}x '{}' to cart.", item.quantity(), selectedProduct.name());
		} else {
			log.warn("SKIPPED: '{}' is unavailable — not added to cart.", selectedProduct.name());
			recordSkippedItem(item.query());
		}
	}

	private SearchResult resolveProduct(SupermarketDriver driver, ShoppingItem item, String targetSupermarket)
			throws InterruptedException {
		List<ProductMapping> mappings = productService
				.findMappingsBySearchTextAndSupermarket(item.query().toLowerCase().trim(), targetSupermarket);

		if (!mappings.isEmpty()) {
			for (ProductMapping mapping : mappings) {
				log.info("Trying mapping: '{}' (Priority: {}) -> SKU: {}", item.query(), mapping.getPriority(),
						mapping.getExternalProductId());
				List<SearchResult> skuResults = driver.searchProduct(mapping.getExternalProductId());
				if (!skuResults.isEmpty()) {
					SearchResult result = skuResults.get(0);
					if (driver.isProductAvailable(result.externalId())) {
						return result;
					}
				}
				log.warn("Mapping SKU {} is unavailable, trying next fallback...", mapping.getExternalProductId());
			}
			log.warn("All alternative mappings for '{}' are unavailable. Deferring to end.", item.query());
		} else {
			log.info("No mappings found for query '{}'. Deferring to end.", item.query());
		}

		synchronized (this) {
			this.exhaustedItems.add(item);
		}
		return null;
	}
		}

		synchronized (this) {
			this.exhaustedItems.add(item);
		}
		return null;
	}

	private com.autobuy.web.dto.ResolutionResult pauseAndResolveExhausted(ShoppingItem item, String targetSupermarket,
			SupermarketDriver driver) throws InterruptedException {
		log.info("Performing store search for '{}'...", item.query());
		List<SearchResult> results = driver.searchProduct(item.query());

		CompletableFuture<com.autobuy.web.dto.ResolutionResult> future;
		synchronized (this) {
			this.searchResults = results;
			this.state = AutoBuyState.AWAITING_EXHAUSTED_RESOLUTIONS;
			this.currentResolutionFuture = new CompletableFuture<>();
			future = this.currentResolutionFuture;
		}

		log.info("PAUSED: Awaiting product resolution from the Web UI for '{}'...", item.query());

		com.autobuy.web.dto.ResolutionResult selected = null;
		try {
			selected = awaitFuture(future, "Exhausted mapping resolution");
		} finally {
			synchronized (this) {
				this.searchResults = new ArrayList<>();
				this.currentResolutionFuture = null;
			}
		}

		return selected;
	}

	private synchronized void recordSkippedItem(String query) {
		this.skippedItems.add(query);
	}

	private <T> T awaitFuture(CompletableFuture<T> future, String operationName) throws InterruptedException {
		try {
			return future.get();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw e;
		} catch (java.util.concurrent.ExecutionException e) {
			log.error("Error during {}", operationName, e);
			throw new RuntimeException("Error during " + operationName, e);
		} catch (java.util.concurrent.CancellationException _) {
			log.warn("{} was cancelled.", operationName);
			throw new InterruptedException(operationName + " cancelled.");
		}
	}

	private ResolutionAction pauseAndResolveMapping(String query, List<SearchResult> results)
			throws InterruptedException {
		CompletableFuture<ResolutionAction> future;
		synchronized (this) {
			this.searchResults = new ArrayList<>(results);
			this.state = AutoBuyState.AWAITING_MAPPING;
			this.currentMappingFuture = new CompletableFuture<>();
			future = this.currentMappingFuture;
		}

		log.info("PAUSED: Awaiting product mapping resolution from the Web UI for '{}'...", query);

		ResolutionAction action = null;
		try {
			action = awaitFuture(future, "Mapping resolution");
		} finally {
			synchronized (this) {
				this.searchResults.clear();
				this.currentMappingFuture = null;
			}
		}

		return action;
	}
	private boolean initializeDriverAndLogin(SupermarketDriver driver, String targetSupermarket, String username,
			String password, boolean headless) {
		try {
			log.info("Initializing supermarket driver for {}...", targetSupermarket);
			driver.initialize(username, password, !headless);
			return true;
		} catch (Exception e) {
			log.error("Failed to initialize driver", e);
			updateStateFailure("Failed to initialize driver: " + e.getMessage());
			try {
				driver.close();
			} catch (Exception ex) {
				log.debug("Ignored exception during driver close on failure", ex);
			}
			activeDriver = null;
			return false;
		}
	}

	private void pauseForFinalReview() throws InterruptedException {
		CompletableFuture<Void> future;
		synchronized (this) {
			this.state = AutoBuyState.AWAITING_FINAL_REVIEW;
			this.finalReviewFuture = new CompletableFuture<>();
			future = this.finalReviewFuture;
		}

		try {
			awaitFuture(future, "Final review");
		} finally {
			synchronized (this) {
				this.finalReviewFuture = null;
			}
		}
	}

	private synchronized void updateStateFailure(String message) {
		this.state = AutoBuyState.FAILED;
		this.errorMsg = message;
		log.error("Auto-buy execution failed: {}", message);
	}
}
