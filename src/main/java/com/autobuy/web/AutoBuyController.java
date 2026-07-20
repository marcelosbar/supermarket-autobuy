package com.autobuy.web;

import com.autobuy.config.MemoryAppender;
import com.autobuy.model.SearchResult;
import com.autobuy.model.ShoppingItem;
import com.autobuy.service.AutoBuyExecutionContext;
import com.autobuy.service.AutoBuyOrchestrationService;
import com.autobuy.service.GuestSearchService;
import com.autobuy.service.ProductResolutionService;
import com.autobuy.web.dto.ActionResponse;
import com.autobuy.web.dto.AutoBuyStatusResponse;
import com.autobuy.web.dto.RefineRequest;
import com.autobuy.web.dto.ResolutionResultStatus;
import com.autobuy.web.dto.ResolveRequest;
import com.autobuy.web.dto.ResolveResponse;
import com.autobuy.web.dto.RunRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/**
 * REST Controller exposing endpoints to manage autobuy execution, status, and
 * interactive resolution.
 */
@RestController
@RequestMapping("/autobuy")
public class AutoBuyController {

	private static final Logger log = LoggerFactory.getLogger(AutoBuyController.class);

	private final AutoBuyOrchestrationService autoBuyOrchestrationService;
	private final AutoBuyExecutionContext autoBuyExecutionContext;
	private final ProductResolutionService productResolutionService;
	private final GuestSearchService guestSearchService;

	private static final String DEFAULT_LIST_PATH = "shopping-list.json";
	private static final String DEFAULT_SUPERMARKET = "CONTINENTE";

	public AutoBuyController(AutoBuyOrchestrationService autoBuyOrchestrationService,
			AutoBuyExecutionContext autoBuyExecutionContext, ProductResolutionService productResolutionService,
			GuestSearchService guestSearchService) {
		this.autoBuyOrchestrationService = autoBuyOrchestrationService;
		this.autoBuyExecutionContext = autoBuyExecutionContext;
		this.productResolutionService = productResolutionService;
		this.guestSearchService = guestSearchService;
	}

	@PostMapping("/run")
	public ResponseEntity<ActionResponse> runAutoBuy(@RequestBody(required = false) RunRequest request) {
		String supermarket = (request != null && request.supermarket() != null)
				? request.supermarket()
				: DEFAULT_SUPERMARKET;
		boolean headless = request != null && Boolean.TRUE.equals(request.headless());

		autoBuyOrchestrationService.startAutoBuy(DEFAULT_LIST_PATH, supermarket, headless);

		return ResponseEntity.ok(new ActionResponse(true, "Auto-Buy started successfully."));
	}

	@GetMapping("/status")
	public ResponseEntity<AutoBuyStatusResponse> getStatus() {
		List<String> exhaustedQueries = autoBuyExecutionContext.getExhaustedItems().stream().map(ShoppingItem::query)
				.toList();
		AutoBuyStatusResponse status = new AutoBuyStatusResponse(autoBuyExecutionContext.getState(),
				autoBuyExecutionContext.getCurrentItemQuery(), autoBuyExecutionContext.getCurrentItemQuantity(),
				autoBuyExecutionContext.getSearchResults(), new ArrayList<>(MemoryAppender.getLogs()),
				autoBuyExecutionContext.getErrorMsg(), autoBuyExecutionContext.getSkippedItems(), exhaustedQueries,
				autoBuyExecutionContext.isBrowserOpen(), autoBuyExecutionContext.getMappingInstructions());
		return ResponseEntity.ok(status);
	}

	@PostMapping("/resolve")
	public ResponseEntity<ResolveResponse> resolveMapping(@RequestBody ResolveRequest request) {
		ResolutionResultStatus status = productResolutionService.resolveMapping(request.externalId(),
				request.saveMapping());
		boolean added = status == null || status.added();
		String message = status != null ? status.message() : "Successfully resolved.";
		return ResponseEntity.ok(new ResolveResponse(true, added, message));
	}

	@GetMapping("/search")
	public ResponseEntity<List<SearchResult>> searchSupermarket(@RequestParam String query,
			@RequestParam(defaultValue = DEFAULT_SUPERMARKET) String supermarket) {
		String sanitizedQuery = query.replace('\n', '_').replace('\r', '_');
		String sanitizedSupermarket = supermarket.replace('\n', '_').replace('\r', '_');
		log.info("Performing guest search for '{}' in {}", sanitizedQuery, sanitizedSupermarket);

		List<SearchResult> results = guestSearchService.performGuestSearch(query, supermarket);
		return ResponseEntity.ok(results);
	}

	@PostMapping("/refine")
	public ResponseEntity<ActionResponse> refineSearch(@RequestBody RefineRequest request) {
		productResolutionService.refineSearch(request.query());
		return ResponseEntity.ok(new ActionResponse(true));
	}

	@PostMapping("/complete")
	public ResponseEntity<ActionResponse> completeRun(
			@RequestParam(name = "keepBrowser", defaultValue = "false") boolean keepBrowser) {
		autoBuyOrchestrationService.completeRun(keepBrowser);
		return ResponseEntity.ok(new ActionResponse(true));
	}

	@PostMapping("/cancel")
	public ResponseEntity<ActionResponse> cancelRun() {
		autoBuyOrchestrationService.cancel();
		return ResponseEntity.ok(new ActionResponse(true));
	}
}
