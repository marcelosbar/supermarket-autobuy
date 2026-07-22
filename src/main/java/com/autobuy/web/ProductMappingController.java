package com.autobuy.web;

import com.autobuy.model.ProductMapping;
import com.autobuy.model.SearchResult;
import com.autobuy.service.ProductService;
import com.autobuy.web.dto.ActionResponse;
import com.autobuy.web.dto.AddAlternativeRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Controller exposing product mapping endpoints.
 */
@RestController
@RequestMapping("/mappings")
public class ProductMappingController {

	private static final Logger log = LoggerFactory.getLogger(ProductMappingController.class);

	private final ProductService productService;

	public ProductMappingController(ProductService productService) {
		this.productService = productService;
	}

	@GetMapping
	public ResponseEntity<Map<String, List<ProductMapping>>> getMappings() {
		List<ProductMapping> all = productService.findAllMappings();
		Map<String, List<ProductMapping>> grouped = all.stream().collect(Collectors
				.groupingBy(ProductMapping::getSearchText, Collectors.collectingAndThen(Collectors.toList(), list -> {
					list.sort(Comparator.comparingInt(ProductMapping::getPriority));
					return list;
				})));
		return ResponseEntity.ok(grouped);
	}

	@PostMapping("/{id}/promote")
	public ResponseEntity<ActionResponse> promoteMapping(@PathVariable Long id) {
		productService.promoteMapping(id);
		return ResponseEntity.ok(new ActionResponse(true));
	}

	@PostMapping("/{id}/demote")
	public ResponseEntity<ActionResponse> demoteMapping(@PathVariable Long id) {
		productService.demoteMapping(id);
		return ResponseEntity.ok(new ActionResponse(true));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> deleteMapping(@PathVariable Long id) {
		if (productService.findMappingById(id).isPresent()) {
			productService.deleteMapping(id);
			log.info("Deleted product mapping ID {}", id);
			return ResponseEntity.noContent().build();
		}
		return ResponseEntity.notFound().build();
	}

	@PostMapping("/alternative")
	public ResponseEntity<ActionResponse> addAlternative(@RequestBody AddAlternativeRequest request) {
		List<ProductMapping> existing = productService.findMappingsBySearchTextAndSupermarket(
				request.searchText().toLowerCase().trim(), request.supermarket());
		int nextPriority = existing.stream().mapToInt(ProductMapping::getPriority).max().orElse(-1) + 1;

		SearchResult result = new SearchResult(request.externalId(), request.productName(), "",
				java.math.BigDecimal.ZERO, "", "");
		productService.saveMappingWithPriority(request.searchText().toLowerCase().trim(), request.supermarket(), result,
				nextPriority);

		return ResponseEntity.ok(new ActionResponse(true));
	}
}
