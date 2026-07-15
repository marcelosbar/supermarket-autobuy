package com.autobuy.service;

import com.autobuy.driver.SupermarketDriver;
import com.autobuy.model.AutoBuyState;
import com.autobuy.model.ProductMapping;
import com.autobuy.model.ResolutionAction;
import com.autobuy.model.SearchResult;
import com.autobuy.model.ShoppingItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Handles interactive product mapping resolution, mapping lookup, and pauses.
 */
@Service
public class ProductResolutionService {

	private static final Logger log = LoggerFactory.getLogger(ProductResolutionService.class);

	private final ProductService productService;
	private final PriceHistoryService priceHistoryService;
	private final AutoBuyExecutionContext executionContext;

	private CompletableFuture<ResolutionAction> currentMappingFuture = null;
	private CompletableFuture<com.autobuy.web.dto.ResolutionResult> currentResolutionFuture = null;
	private CompletableFuture<Boolean> additionValidationFuture = null;

	public ProductResolutionService(ProductService productService, PriceHistoryService priceHistoryService,
			AutoBuyExecutionContext executionContext) {
		this.productService = productService;
		this.priceHistoryService = priceHistoryService;
		this.executionContext = executionContext;
	}

	public ResolveResult resolveProduct(SupermarketDriver driver, ShoppingItem item, String targetSupermarket)
			throws InterruptedException {
		List<ProductMapping> mappings = productService
				.findMappingsBySearchTextAndSupermarket(item.query().toLowerCase().trim(), targetSupermarket);

		if (!mappings.isEmpty()) {
			return resolveFromMappings(driver, item, mappings);
		} else {
			return performStoreSearchAndResolve(driver, item, targetSupermarket);
		}
	}

	private ResolveResult resolveFromMappings(SupermarketDriver driver, ShoppingItem item,
			List<ProductMapping> mappings) {
		for (ProductMapping mapping : mappings) {
			log.info("Trying mapping: '{}' (Priority: {}) -> SKU: {}", item.query(), mapping.getPriority(),
					mapping.getExternalProductId());
			List<SearchResult> skuResults = driver.searchProduct(mapping.getExternalProductId());
			if (!skuResults.isEmpty()) {
				SearchResult result = skuResults.get(0);
				if (driver.isProductAvailable(result.externalId())) {
					return new ResolveResult(result, false);
				}
			}
			log.warn("Mapping SKU {} is unavailable, trying next fallback...", mapping.getExternalProductId());
		}
		log.warn("All alternative mappings for '{}' are unavailable. Deferring to end.", item.query());
		synchronized (executionContext) {
			executionContext.getExhaustedItems().add(item);
		}
		return null;
	}

	private record SelectResult(SearchResult product, boolean shouldIncrementPriority) {
	}

	private SelectResult handleSelectAction(ResolutionAction action, SupermarketDriver driver, ShoppingItem item,
			String targetSupermarket, List<SearchResult> searchResultsList, int priority) {
		String externalId = action.value();
		SearchResult selectedProduct = searchResultsList.stream().filter(r -> r.externalId().equals(externalId))
				.findFirst().orElse(null);

		if (selectedProduct == null) {
			log.warn("Selected product SKU {} not found in current search results.", externalId);
			return new SelectResult(null, false);
		}

		if (action.saveMapping()) {
			saveProductMappingSafely(item, selectedProduct, targetSupermarket, priority);
		}

		boolean added = driver.addProductToCart(selectedProduct.externalId(), item.quantity());
		if (added) {
			completeAdditionValidation(true);
			return new SelectResult(selectedProduct, false);
		} else {
			completeAdditionValidation(false);
			log.warn("Selected product SKU {} could not be added to cart. Prompting user for fallback alternative...",
					selectedProduct.externalId());
			markProductUnavailableInList(searchResultsList, selectedProduct.externalId());
			return new SelectResult(null, true);
		}
	}

	private ResolveResult performStoreSearchAndResolve(SupermarketDriver driver, ShoppingItem item,
			String targetSupermarket) throws InterruptedException {
		log.info("No mapping found for query '{}'. Performing store search...", item.query());
		List<SearchResult> searchResultsList = driver.searchProduct(item.query());
		if (searchResultsList.isEmpty()) {
			log.warn("No products found for query '{}'. Skipping.", item.query());
			executionContext.recordSkippedItem(item.query());
			return null;
		}

		int priority = 0;
		SearchResult finalSelectedProduct = null;
		String searchWord = item.query();
		boolean resolved = false;

		while (!resolved && !Thread.currentThread().isInterrupted()) {
			ResolutionAction action = pauseAndResolveMapping(searchWord, searchResultsList, priority);
			if (action == null || action.type() == ResolutionAction.ActionType.SKIP) {
				handleSkipAction(item, priority);
				resolved = true;
			} else if (action.type() == ResolutionAction.ActionType.REFINE) {
				String newQuery = action.value();
				log.info("Refining search for '{}' with new query: '{}'...", item.query(), newQuery);
				searchWord = newQuery;
				searchResultsList = driver.searchProduct(searchWord);
			} else {
				SelectResult result = handleSelectAction(action, driver, item, targetSupermarket, searchResultsList,
						priority);
				if (result.product() != null) {
					finalSelectedProduct = result.product();
					resolved = true;
				} else if (result.shouldIncrementPriority()) {
					priority++;
				}
			}
		}

		return new ResolveResult(finalSelectedProduct, true);
	}

	private void handleSkipAction(ShoppingItem item, int priority) {
		log.info("Skipped mapping for '{}' on user prompt.", item.query());
		if (priority == 0) {
			executionContext.recordSkippedItem(item.query());
		} else {
			log.warn("User skipped fallback prompts for '{}'. Deferring to end.", item.query());
			synchronized (executionContext) {
				executionContext.getExhaustedItems().add(item);
			}
		}
		completeAdditionValidation(true);
	}

	public void saveProductMappingSafely(ShoppingItem item, SearchResult selectedProduct, String targetSupermarket,
			int priority) {
		try {
			productService.saveMappingWithPriority(item.query().toLowerCase().trim(), targetSupermarket,
					selectedProduct, priority);
			log.info("Saved product mapping: '{}' -> SKU: {} (Priority: {})", item.query(),
					selectedProduct.externalId(), priority);
		} catch (Exception e) {
			log.error("Failed to save product mapping", e);
		}
	}

	private void saveProductMappingSafely(ShoppingItem failedItem, SearchResult selected, String targetSupermarket) {
		try {
			List<ProductMapping> existing = productService
					.findMappingsBySearchTextAndSupermarket(failedItem.query().toLowerCase().trim(), targetSupermarket);
			int nextPriority = existing.stream().mapToInt(ProductMapping::getPriority).max().orElse(-1) + 1;
			productService.saveMappingWithPriority(failedItem.query().toLowerCase().trim(), targetSupermarket, selected,
					nextPriority);
			log.info("Saved product mapping: '{}' -> SKU: {} (Priority: {})", failedItem.query(), selected.externalId(),
					nextPriority);
		} catch (Exception e) {
			log.error("Failed to save product mapping", e);
		}
	}

	private void markProductUnavailableInList(List<SearchResult> searchResultsList, String externalId) {
		for (int i = 0; i < searchResultsList.size(); i++) {
			SearchResult r = searchResultsList.get(i);
			if (r.externalId().equals(externalId)) {
				searchResultsList.set(i,
						new SearchResult(r.externalId(), r.name(), r.brand(), r.price(), r.url(), r.category(), false));
			}
		}
	}

	public com.autobuy.web.dto.ResolutionResultStatus resolveMapping(String externalId, boolean saveMapping) {
		CompletableFuture<ResolutionAction> mappingFuture = null;

		synchronized (executionContext) {
			AutoBuyState state = executionContext.getState();
			if (state != AutoBuyState.AWAITING_MAPPING && state != AutoBuyState.AWAITING_EXHAUSTED_RESOLUTIONS) {
				throw new IllegalStateException("Not currently waiting for product mapping resolution.");
			}

			if (state == AutoBuyState.AWAITING_EXHAUSTED_RESOLUTIONS) {
				return handleAwaitingExhaustedResolutions(externalId, saveMapping);
			}

			// AWAITING_MAPPING path
			mappingFuture = this.currentMappingFuture;
			if (mappingFuture == null) {
				throw new IllegalStateException("No active mapping future.");
			}

			if (externalId == null || externalId.isBlank() || "skip".equalsIgnoreCase(externalId.trim())) {
				executionContext.transitionTo(AutoBuyState.RUNNING);
				this.currentMappingFuture = null;
				mappingFuture.complete(new ResolutionAction(ResolutionAction.ActionType.SKIP, null));
				return new com.autobuy.web.dto.ResolutionResultStatus(true, "Skipped mapping.");
			}

			SearchResult selected = executionContext.getSearchResults().stream()
					.filter(r -> r.externalId().equals(externalId)).findFirst().orElse(null);

			if (selected == null) {
				throw new IllegalArgumentException("Selected externalId not found in current search results.");
			}

			this.additionValidationFuture = new CompletableFuture<>();
			this.currentMappingFuture = null;
			mappingFuture.complete(new ResolutionAction(ResolutionAction.ActionType.SELECT, externalId, saveMapping));
		}

		return waitForAdditionValidation(saveMapping);
	}

	private com.autobuy.web.dto.ResolutionResultStatus handleAwaitingExhaustedResolutions(String externalId,
			boolean saveMapping) {
		CompletableFuture<com.autobuy.web.dto.ResolutionResult> resFuture = this.currentResolutionFuture;
		if (resFuture == null) {
			throw new IllegalStateException("No active resolution future.");
		}

		if (externalId == null || externalId.isBlank() || "skip".equalsIgnoreCase(externalId.trim())) {
			executionContext.transitionTo(AutoBuyState.RUNNING);
			this.currentResolutionFuture = null;
			resFuture.complete(null);
			return new com.autobuy.web.dto.ResolutionResultStatus(true, "Skipped exhausted resolution.");
		}

		SupermarketDriver activeDriver = executionContext.isBrowserOpen() ? getActiveDriverFromContext() : null;
		if (activeDriver != null) {
			boolean available = activeDriver.isProductAvailable(externalId);
			if (!available) {
				throw new IllegalArgumentException(
						"The selected product is out of stock or unavailable. Please choose another.");
			}
		}

		SearchResult selected = executionContext.getSearchResults().stream()
				.filter(r -> r.externalId().equals(externalId)).findFirst().orElse(null);

		if (selected == null) {
			throw new IllegalArgumentException("Selected externalId not found in current search results.");
		}

		this.currentResolutionFuture = null;
		executionContext.transitionTo(AutoBuyState.RUNNING);
		resFuture.complete(new com.autobuy.web.dto.ResolutionResult(selected, saveMapping));
		return new com.autobuy.web.dto.ResolutionResultStatus(true, "Successfully resolved.");
	}

	private SupermarketDriver getActiveDriverFromContext() {
		return executionContext.getActiveDriver();
	}

	public void refineSearch(String newQuery) {
		synchronized (executionContext) {
			if (executionContext.getState() != AutoBuyState.AWAITING_MAPPING) {
				throw new IllegalStateException("Not currently waiting for product mapping resolution.");
			}

			CompletableFuture<ResolutionAction> future = this.currentMappingFuture;
			if (future == null) {
				return;
			}

			if (newQuery == null || newQuery.isBlank()) {
				throw new IllegalArgumentException("Refined search query cannot be blank.");
			}

			executionContext.transitionTo(AutoBuyState.RUNNING);
			this.currentMappingFuture = null;
			future.complete(new ResolutionAction(ResolutionAction.ActionType.REFINE, newQuery));
		}
	}

	private com.autobuy.web.dto.ResolutionResultStatus waitForAdditionValidation(boolean saveMapping) {
		try {
			Boolean added = this.additionValidationFuture.get(15, java.util.concurrent.TimeUnit.SECONDS);
			if (added == null || !added) {
				synchronized (executionContext) {
					if (!saveMapping) {
						executionContext.transitionTo(AutoBuyState.AWAITING_MAPPING);
						throw new IllegalArgumentException(
								"This product is out of stock. Please select an in-stock product.");
					} else {
						return new com.autobuy.web.dto.ResolutionResultStatus(false,
								"Saved as mapping, but out of stock. Please select a fallback alternative.");
					}
				}
			}
			synchronized (executionContext) {
				if (executionContext.getState() == AutoBuyState.AWAITING_MAPPING) {
					executionContext.transitionTo(AutoBuyState.RUNNING);
				}
			}
			return new com.autobuy.web.dto.ResolutionResultStatus(true, "Successfully added to cart.");
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			synchronized (executionContext) {
				executionContext.transitionTo(AutoBuyState.AWAITING_MAPPING);
			}
			throw new com.autobuy.exception.AutoBuyException("Validation interrupted", e);
		} catch (Exception e) {
			synchronized (executionContext) {
				executionContext.transitionTo(AutoBuyState.AWAITING_MAPPING);
			}
			throw new com.autobuy.exception.AutoBuyException("Validation failed or timed out: " + e.getMessage(), e);
		} finally {
			synchronized (executionContext) {
				this.additionValidationFuture = null;
			}
		}
	}

	public void completeAdditionValidation(boolean success) {
		CompletableFuture<Boolean> future = this.additionValidationFuture;
		if (future != null) {
			future.complete(success);
		}
	}

	private ResolutionAction pauseAndResolveMapping(String query, List<SearchResult> results, int priority)
			throws InterruptedException {
		CompletableFuture<ResolutionAction> future;
		synchronized (executionContext) {
			executionContext.setSearchResults(results);
			executionContext.transitionTo(AutoBuyState.AWAITING_MAPPING);
			executionContext.setMappingInstructions(priority == 0
					? ""
					: "The previous selection was out of stock. Please select a fallback alternative product (Priority "
							+ priority + ").");
			this.currentMappingFuture = new CompletableFuture<>();
			future = this.currentMappingFuture;
		}

		log.info("PAUSED: Awaiting product mapping resolution from the Web UI for '{}' (Priority {})...", query,
				priority);

		ResolutionAction action = null;
		try {
			action = awaitFuture(future, "Mapping resolution");
		} finally {
			synchronized (executionContext) {
				this.currentMappingFuture = null;
				executionContext.setMappingInstructions("");
			}
		}

		return action;
	}

	private com.autobuy.web.dto.ResolutionResult pauseAndResolveExhausted(ShoppingItem item, SupermarketDriver driver)
			throws InterruptedException {
		log.info("Performing store search for '{}'...", item.query());
		List<SearchResult> results = driver.searchProduct(item.query());

		CompletableFuture<com.autobuy.web.dto.ResolutionResult> future;
		synchronized (executionContext) {
			executionContext.setSearchResults(results);
			executionContext.transitionTo(AutoBuyState.AWAITING_EXHAUSTED_RESOLUTIONS);
			this.currentResolutionFuture = new CompletableFuture<>();
			future = this.currentResolutionFuture;
		}

		log.info("PAUSED: Awaiting product resolution from the Web UI for '{}'...", item.query());

		com.autobuy.web.dto.ResolutionResult selected = null;
		try {
			selected = awaitFuture(future, "Exhausted mapping resolution");
		} finally {
			synchronized (executionContext) {
				this.currentResolutionFuture = null;
			}
		}

		return selected;
	}

	public List<ShoppingItem> partitionByMappingStatus(List<ShoppingItem> items, String supermarket) {
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

	public void resolveExhaustedItems(SupermarketDriver driver, String targetSupermarket) throws InterruptedException {
		List<ShoppingItem> exhaustedItems = executionContext.getExhaustedItems();
		log.info("Awaiting user resolution for {} unavailable/unmapped items...", exhaustedItems.size());
		while (!exhaustedItems.isEmpty() && executionContext.getState() != AutoBuyState.FAILED
				&& !Thread.currentThread().isInterrupted()) {
			ShoppingItem failedItem = exhaustedItems.get(0);
			synchronized (executionContext) {
				executionContext.setCurrentItemQuery(failedItem.query());
				executionContext.setCurrentItemQuantity(failedItem.quantity());
			}
			com.autobuy.web.dto.ResolutionResult resolution = pauseAndResolveExhausted(failedItem, driver);
			if (resolution == null) {
				log.info("Skipped item '{}' on user resolution prompt.", failedItem.query());
				executionContext.recordSkippedItem(failedItem.query());
			} else {
				SearchResult selected = resolution.product();
				boolean success = driver.addProductToCart(selected.externalId(), failedItem.quantity());
				if (success) {
					log.info("SUCCESS: Added {}x '{}' to cart.", failedItem.quantity(), selected.name());
					logPriceHistorySafely(selected, targetSupermarket);
					if (resolution.saveMapping()) {
						saveProductMappingSafely(failedItem, selected, targetSupermarket);
					}
				} else {
					log.warn("SKIPPED: '{}' is unavailable — not added to cart.", selected.name());
					executionContext.recordSkippedItem(failedItem.query());
				}
			}
			synchronized (executionContext) {
				exhaustedItems.remove(failedItem);
			}
		}
	}

	public void logPriceHistorySafely(SearchResult selected, String targetSupermarket) {
		try {
			priceHistoryService.logPrice(selected, targetSupermarket);
			log.info("Logged price for {}: {} €", selected.name(), selected.price());
		} catch (Exception e) {
			log.error("Failed to log price history", e);
		}
	}

	public void cancel() {
		synchronized (executionContext) {
			if (currentMappingFuture != null) {
				currentMappingFuture.cancel(true);
				currentMappingFuture = null;
			}
			if (currentResolutionFuture != null) {
				currentResolutionFuture.cancel(true);
				currentResolutionFuture = null;
			}
			if (additionValidationFuture != null) {
				additionValidationFuture.cancel(true);
				additionValidationFuture = null;
			}
		}
	}
}
