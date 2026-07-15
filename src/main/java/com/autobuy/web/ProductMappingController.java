package com.autobuy.web;

import com.autobuy.model.ProductMapping;
import com.autobuy.model.SearchResult;
import com.autobuy.service.ProductService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller exposing product mapping endpoints.
 */
@RestController
@RequestMapping("/api")
public class ProductMappingController {

	private static final Logger log = LoggerFactory.getLogger(ProductMappingController.class);

	private final ProductService productService;

	private static final String SUCCESS_KEY = "success";
	private static final String MESSAGE_KEY = "message";

	public ProductMappingController(ProductService productService) {
		this.productService = productService;
	}

	@GetMapping("/mappings")
	public ResponseEntity<Map<String, List<ProductMapping>>> getMappings() {
		List<ProductMapping> all = productService.findAllMappings();
		Map<String, List<ProductMapping>> grouped = all.stream()
				.collect(java.util.stream.Collectors.groupingBy(ProductMapping::getSearchText,
						java.util.stream.Collectors.collectingAndThen(java.util.stream.Collectors.toList(), list -> {
							list.sort(java.util.Comparator.comparingInt(ProductMapping::getPriority));
							return list;
						})));
		return ResponseEntity.ok(grouped);
	}

	@PostMapping("/mappings/{id}/promote")
	public ResponseEntity<Map<String, Object>> promoteMapping(@PathVariable Long id) {
		try {
			productService.promoteMapping(id);
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

	@PostMapping("/mappings/{id}/demote")
	public ResponseEntity<Map<String, Object>> demoteMapping(@PathVariable Long id) {
		try {
			productService.demoteMapping(id);
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

	@DeleteMapping("/mappings/{id}")
	public ResponseEntity<Void> deleteMapping(@PathVariable Long id) {
		if (productService.findMappingById(id).isPresent()) {
			productService.deleteMapping(id);
			log.info("Deleted product mapping ID {}", id);
			return ResponseEntity.noContent().build();
		}
		return ResponseEntity.notFound().build();
	}

	@PostMapping("/autobuy/add-alternative")
	public ResponseEntity<Map<String, Object>> addAlternative(
			@RequestBody com.autobuy.web.dto.AddAlternativeRequest request) {
		try {
			List<ProductMapping> existing = productService.findMappingsBySearchTextAndSupermarket(
					request.searchText().toLowerCase().trim(), request.supermarket());
			int nextPriority = existing.stream().mapToInt(ProductMapping::getPriority).max().orElse(-1) + 1;

			SearchResult result = new SearchResult(request.externalId(), request.productName(), "",
					java.math.BigDecimal.ZERO, "", "");
			productService.saveMappingWithPriority(request.searchText().toLowerCase().trim(), request.supermarket(),
					result, nextPriority);

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
