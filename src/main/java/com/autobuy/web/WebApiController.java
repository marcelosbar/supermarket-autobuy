package com.autobuy.web;

import com.autobuy.model.SearchResult;
import com.autobuy.service.AutoBuyExecutionContext;
import com.autobuy.service.AutoBuyOrchestrationService;
import com.autobuy.service.GuestSearchService;
import com.autobuy.service.ProductResolutionService;
import com.autobuy.web.dto.AutoBuyStatusResponse;
import com.autobuy.web.dto.RefineRequest;
import com.autobuy.web.dto.ResolveRequest;
import com.autobuy.web.dto.RunRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST Controller exposing endpoints to manage autobuy execution, status, and
 * interactive resolution.
 */
@RestController
@RequestMapping("/autobuy")
public class WebApiController {

	private static final Logger log = LoggerFactory.getLogger(WebApiController.class);

	private final AutoBuyOrchestrationService autoBuyOrchestrationService;
	private final AutoBuyExecutionContext autoBuyExecutionContext;
	private final ProductResolutionService productResolutionService;
	private final GuestSearchService guestSearchService;

	private static final String DEFAULT_LIST_PATH = "shopping-list.json";
	private static final String SUCCESS_KEY = "success";
	private static final String MESSAGE_KEY = "message";
	private static final String DEFAULT_SUPERMARKET = "CONTINENTE";

	public WebApiController(AutoBuyOrchestrationService autoBuyOrchestrationService,
			AutoBuyExecutionContext autoBuyExecutionContext, ProductResolutionService productResolutionService,
			GuestSearchService guestSearchService) {
		this.autoBuyOrchestrationService = autoBuyOrchestrationService;
		this.autoBuyExecutionContext = autoBuyExecutionContext;
		this.productResolutionService = productResolutionService;
		this.guestSearchService = guestSearchService;
	}

	@PostMapping("/run")
	public ResponseEntity<Map<String, Object>> runAutoBuy(@RequestBody RunRequest request) {
		try {
			boolean headless = false;
			String supermarket = request.supermarket() != null ? request.supermarket() : DEFAULT_SUPERMARKET;

			guestSearchService.close(); // Close any active guest search driver to prevent conflicts
			autoBuyOrchestrationService.startAutoBuy(DEFAULT_LIST_PATH, supermarket, headless);

			Map<String, Object> response = new HashMap<>();
			response.put(SUCCESS_KEY, true);
			response.put(MESSAGE_KEY, "Auto-Buy started successfully.");
			return ResponseEntity.ok(response);
		} catch (IllegalStateException e) {
			Map<String, Object> response = new HashMap<>();
			response.put(SUCCESS_KEY, false);
			response.put(MESSAGE_KEY, e.getMessage());
			return ResponseEntity.badRequest().body(response);
		}
	}

	@GetMapping("/status")
	public ResponseEntity<AutoBuyStatusResponse> getStatus() {
		return ResponseEntity.ok(autoBuyExecutionContext.getStatus());
	}

	@PostMapping("/resolve")
	public ResponseEntity<Map<String, Object>> resolveMapping(@RequestBody ResolveRequest request) {
		try {
			com.autobuy.web.dto.ResolutionResultStatus status = productResolutionService
					.resolveMapping(request.externalId(), request.saveMapping());
			Map<String, Object> response = new HashMap<>();
			response.put(SUCCESS_KEY, true);
			if (status != null) {
				response.put("added", status.added());
				response.put(MESSAGE_KEY, status.message());
			} else {
				response.put("added", true);
				response.put(MESSAGE_KEY, "Successfully resolved.");
			}
			return ResponseEntity.ok(response);
		} catch (Exception e) {
			Map<String, Object> response = new HashMap<>();
			response.put(SUCCESS_KEY, false);
			response.put(MESSAGE_KEY, e.getMessage());
			return ResponseEntity.badRequest().body(response);
		}
	}

	@GetMapping("/search")
	public ResponseEntity<List<SearchResult>> searchSupermarket(@RequestParam String query,
			@RequestParam(defaultValue = "CONTINENTE") String supermarket) {
		String sanitizedQuery = query.replace('\n', '_').replace('\r', '_');
		String sanitizedSupermarket = supermarket.replace('\n', '_').replace('\r', '_');
		log.info("Performing guest search for '{}' in {}", sanitizedQuery, sanitizedSupermarket);
		try {
			List<SearchResult> results = guestSearchService.performGuestSearch(query, supermarket);
			return ResponseEntity.ok(results);
		} catch (Exception e) {
			log.error("Failed to perform guest search", e);
			return ResponseEntity.internalServerError().build();
		}
	}

	@PostMapping("/refine")
	public ResponseEntity<Map<String, Object>> refineSearch(@RequestBody RefineRequest request) {
		try {
			productResolutionService.refineSearch(request.query());
			Map<String, Object> response = new HashMap<>();
			response.put(SUCCESS_KEY, true);
			return ResponseEntity.ok(response);
		} catch (Exception e) {
			Map<String, Object> response = new HashMap<>();
			response.put(SUCCESS_KEY, false);
			response.put(MESSAGE_KEY, e.getMessage());
			return ResponseEntity.badRequest().body(response);
		}
	}

	@PostMapping("/complete")
	public ResponseEntity<Map<String, Object>> completeRun(
			@RequestParam(name = "keepBrowser", defaultValue = "false") boolean keepBrowser) {
		try {
			autoBuyOrchestrationService.completeRun(keepBrowser);
			Map<String, Object> response = new HashMap<>();
			response.put(SUCCESS_KEY, true);
			return ResponseEntity.ok(response);
		} catch (Exception e) {
			Map<String, Object> response = new HashMap<>();
			response.put(SUCCESS_KEY, false);
			response.put(MESSAGE_KEY, e.getMessage());
			return ResponseEntity.badRequest().body(response);
		}
	}

	@PostMapping("/cancel")
	public ResponseEntity<Map<String, Object>> cancelRun() {
		try {
			autoBuyOrchestrationService.cancel();
			Map<String, Object> response = new HashMap<>();
			response.put(SUCCESS_KEY, true);
			return ResponseEntity.ok(response);
		} catch (Exception e) {
			Map<String, Object> response = new HashMap<>();
			response.put(SUCCESS_KEY, false);
			response.put(MESSAGE_KEY, e.getMessage());
			return ResponseEntity.badRequest().body(response);
		}
	}
}
