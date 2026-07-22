package com.autobuy.service;

import com.autobuy.config.MemoryAppender;
import com.autobuy.driver.SupermarketDriver;
import com.autobuy.model.AutoBuyState;
import com.autobuy.model.ShoppingItem;
import com.autobuy.model.SearchResult;
import com.autobuy.model.ResolveResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Service;
import jakarta.annotation.PreDestroy;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

/**
 * Service to orchestrate the Auto-Buy process in the background.
 */
@Service
public class AutoBuyOrchestrationService {

	private static final Logger log = LoggerFactory.getLogger(AutoBuyOrchestrationService.class);

	private final List<SupermarketDriver> drivers;
	private final AutoBuyExecutionContext executionContext;
	private final ProductResolutionService productResolutionService;
	private final AsyncTaskExecutor taskExecutor;
	private final ExecutionProviders executionProviders;

	private boolean keepBrowserOpen = false;
	private Future<?> currentExecutionFuture = null;
	private CompletableFuture<Void> finalReviewFuture = null;

	public AutoBuyOrchestrationService(List<SupermarketDriver> drivers, AutoBuyExecutionContext executionContext,
			ProductResolutionService productResolutionService, AsyncTaskExecutor autoBuyTaskExecutor,
			ExecutionProviders executionProviders) {
		this.drivers = drivers;
		this.executionContext = executionContext;
		this.productResolutionService = productResolutionService;
		this.taskExecutor = autoBuyTaskExecutor;
		this.executionProviders = executionProviders;
	}

	public synchronized void startAutoBuy(String listPath, String targetSupermarket, boolean headless) {
		AutoBuyState state = executionContext.getState();
		if (state == AutoBuyState.AWAITING_FINAL_REVIEW) {
			log.info("Closing previous browser session and resetting state before starting new run...");
			if (finalReviewFuture != null) {
				finalReviewFuture.complete(null);
				finalReviewFuture = null;
			}
			SupermarketDriver activeDriver = executionContext.getActiveDriver();
			if (activeDriver != null) {
				try {
					activeDriver.close();
				} catch (Exception e) {
					log.error("Error closing previous driver", e);
				}
				executionContext.setActiveDriver(null);
			}
			executionContext.transitionTo(AutoBuyState.IDLE);
		}

		state = executionContext.getState();
		if (state == AutoBuyState.RUNNING || state == AutoBuyState.AWAITING_MAPPING
				|| state == AutoBuyState.AWAITING_EXHAUSTED_RESOLUTIONS) {
			throw new IllegalStateException("An auto-buy execution is already in progress.");
		}

		executionProviders.getGuestSearchService().close();
		MemoryAppender.clear();

		SupermarketDriver activeDriver = executionContext.getActiveDriver();
		if (activeDriver != null) {
			log.info("Closing previous browser session before starting new run...");
			try {
				activeDriver.close();
			} catch (Exception e) {
				log.error("Error closing previous driver", e);
			}
			executionContext.setActiveDriver(null);
		}

		executionContext.reset();
		this.keepBrowserOpen = false;

		log.info("Starting background auto-buy run for {}...", targetSupermarket);

		synchronized (this) {
			currentExecutionFuture = taskExecutor.submit(() -> runExecutionFlow(listPath, targetSupermarket, headless));
		}
	}

	private void runExecutionFlow(String listPath, String targetSupermarket, boolean headless) {
		Future<?> myFuture;
		synchronized (this) {
			myFuture = this.currentExecutionFuture;
		}

		SupermarketDriver driver = drivers.stream()
				.filter(d -> d.getSupermarketName().equalsIgnoreCase(targetSupermarket)).findFirst().orElse(null);

		if (driver == null) {
			executionContext.updateStateFailure("No driver found for supermarket: " + targetSupermarket);
			return;
		}

		executionContext.setActiveDriver(driver);

		List<ShoppingItem> shoppingList = executionProviders.getShoppingListProvider().getShoppingList(listPath);
		if (shoppingList.isEmpty()) {
			executionContext.updateStateFailure("Shopping list is empty or file not found: " + listPath);
			executionContext.setActiveDriver(null);
			return;
		}

		String username = executionProviders.getCredentialProvider().getUsername(targetSupermarket);
		String password = executionProviders.getCredentialProvider().getPassword(targetSupermarket);

		if (username == null || username.isBlank() || password == null || password.isBlank()) {
			executionContext.updateStateFailure("Credentials for " + targetSupermarket
					+ " are not configured. Please save credentials in the Web UI first.");
			executionContext.setActiveDriver(null);
			return;
		}

		if (!initializeDriverAndLogin(driver, targetSupermarket, username, password, headless)) {
			return;
		}

		try {
			processShoppingList(driver, shoppingList, targetSupermarket);
		} finally {
			if (!keepBrowserOpen) {
				try {
					driver.close();
				} catch (Exception e) {
					log.error("Error closing driver", e);
				}
				if (executionContext.getActiveDriver() == driver) {
					executionContext.setActiveDriver(null);
				}
			} else {
				log.info("Keeping browser session open as requested.");
			}
			synchronized (this) {
				if (this.currentExecutionFuture == myFuture) {
					AutoBuyState state = executionContext.getState();
					if (state == AutoBuyState.RUNNING || state == AutoBuyState.AWAITING_FINAL_REVIEW) {
						executionContext.transitionTo(AutoBuyState.SUCCESS);
					}
					currentExecutionFuture = null;
				}
			}
			log.info("Auto-buy background thread completed.");
		}
	}

	private void processShoppingList(SupermarketDriver driver, List<ShoppingItem> shoppingList,
			String targetSupermarket) {
		try {
			List<ShoppingItem> orderedList = productResolutionService.partitionByMappingStatus(shoppingList,
					targetSupermarket);
			for (ShoppingItem item : orderedList) {
				if (Thread.currentThread().isInterrupted() || executionContext.getState() == AutoBuyState.FAILED) {
					break;
				}
				processShoppingItem(driver, item, targetSupermarket);
			}

			if (executionContext.getState() != AutoBuyState.FAILED && !executionContext.getExhaustedItems().isEmpty()) {
				productResolutionService.resolveExhaustedItems(driver, targetSupermarket);
			}

			if (executionContext.getState() != AutoBuyState.FAILED) {
				log.info("All items processed successfully!");
				safeNavigateToCart(driver);
				log.info("Awaiting final cart review in browser window...");
				pauseForFinalReview();
			}

		} catch (InterruptedException _) {
			log.warn("Auto-buy thread interrupted.");
			Thread.currentThread().interrupt();
			synchronized (this) {
				AutoBuyState state = executionContext.getState();
				if (state == AutoBuyState.RUNNING || state == AutoBuyState.AWAITING_MAPPING
						|| state == AutoBuyState.AWAITING_EXHAUSTED_RESOLUTIONS) {
					executionContext.transitionTo(AutoBuyState.FAILED);
					executionContext.setErrorMsg("Execution interrupted.");
				}
			}
		} catch (Exception e) {
			log.error("Unexpected error in background auto-buy run", e);
			executionContext.updateStateFailure("Unexpected execution error: " + e.getMessage());
		}
	}

	private void safeNavigateToCart(SupermarketDriver driver) {
		try {
			driver.navigateToCart();
		} catch (Exception e) {
			log.error("Failed to auto-navigate to cart page", e);
		}
	}

	private void processShoppingItem(SupermarketDriver driver, ShoppingItem item, String targetSupermarket)
			throws InterruptedException {
		synchronized (executionContext) {
			executionContext.setCurrentItemQuery(item.query());
			executionContext.setCurrentItemQuantity(item.quantity());
		}

		log.info("Processing item: '{}' (Quantity: {})", item.query(), item.quantity());

		ResolveResult resolveResult = productResolutionService.resolveProduct(driver, item, targetSupermarket);
		if (resolveResult == null || resolveResult.product() == null) {
			return;
		}
		SearchResult selectedProduct = resolveResult.product();

		// Log Price History
		productResolutionService.logPriceHistorySafely(selectedProduct, targetSupermarket);

		boolean success = true;
		if (!resolveResult.alreadyAdded()) {
			success = driver.addProductToCart(selectedProduct.externalId(), item.quantity());
		}

		if (success) {
			log.info("SUCCESS: Added {}x '{}' to cart.", item.quantity(), selectedProduct.name());
		} else {
			log.warn("SKIPPED: '{}' is unavailable — not added to cart. Deferring to end.", selectedProduct.name());
			synchronized (executionContext) {
				executionContext.getExhaustedItems().add(item);
			}
		}
	}

	private boolean initializeDriverAndLogin(SupermarketDriver driver, String targetSupermarket, String username,
			String password, boolean headless) {
		try {
			log.info("Initializing supermarket driver for {}...", targetSupermarket);
			driver.initialize(username, password, !headless);
			return true;
		} catch (Exception e) {
			log.error("Failed to initialize driver", e);
			executionContext.updateStateFailure("Failed to initialize driver: " + e.getMessage());
			try {
				driver.close();
			} catch (Exception ex) {
				log.debug("Ignored exception during driver close on failure", ex);
			}
			executionContext.setActiveDriver(null);
			return false;
		}
	}

	public synchronized void completeRun() {
		completeRun(false);
	}

	public synchronized void completeRun(boolean keepBrowser) {
		if (executionContext.getState() != AutoBuyState.AWAITING_FINAL_REVIEW) {
			throw new IllegalStateException("Not currently waiting for final review.");
		}
		this.keepBrowserOpen = keepBrowser;
		executionContext.transitionTo(AutoBuyState.SUCCESS);
		if (!keepBrowser) {
			executionContext.setActiveDriver(null);
		}
		CompletableFuture<Void> future = this.finalReviewFuture;
		this.finalReviewFuture = null;
		if (future != null) {
			future.complete(null);
		}
	}

	public void cancel() {
		log.warn("Auto-buy run canceled by user.");
		SupermarketDriver driverToClose = null;

		synchronized (this) {
			executionContext.transitionTo(AutoBuyState.FAILED);
			executionContext.setErrorMsg("Execution canceled by user.");

			productResolutionService.cancel();

			if (finalReviewFuture != null) {
				finalReviewFuture.cancel(true);
				finalReviewFuture = null;
			}
			if (currentExecutionFuture != null) {
				currentExecutionFuture.cancel(true);
				currentExecutionFuture = null;
			}

			SupermarketDriver activeDriver = executionContext.getActiveDriver();
			if (activeDriver != null) {
				driverToClose = activeDriver;
				executionContext.setActiveDriver(null);
			}
		}

		if (driverToClose != null) {
			final SupermarketDriver finalDriver = driverToClose;
			java.util.concurrent.CompletableFuture.runAsync(() -> {
				try {
					log.info("Closing driver asynchronously on cancel...");
					finalDriver.close();
					log.info("Driver closed asynchronously on cancel.");
				} catch (Exception e) {
					log.error("Error closing driver asynchronously on cancel", e);
				}
			});
		}
	}

	private void pauseForFinalReview() throws InterruptedException {
		CompletableFuture<Void> future;
		synchronized (this) {
			executionContext.transitionTo(AutoBuyState.AWAITING_FINAL_REVIEW);
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

	@PreDestroy
	public synchronized void shutdown() {
		SupermarketDriver activeDriver = executionContext.getActiveDriver();
		if (activeDriver != null) {
			try {
				activeDriver.close();
			} catch (Exception e) {
				log.debug("Ignore driver close errors on application shutdown", e);
			}
			executionContext.setActiveDriver(null);
		}
	}
}
