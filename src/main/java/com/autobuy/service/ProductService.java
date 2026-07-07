package com.autobuy.service;

import com.autobuy.model.Product;
import com.autobuy.model.ProductMapping;
import com.autobuy.model.SearchResult;
import com.autobuy.repository.ProductMappingRepository;
import com.autobuy.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service to manage products and their supermarket mappings.
 */
@Service
public class ProductService {

	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ProductService.class);

	private final ProductRepository productRepository;
	private final ProductMappingRepository productMappingRepository;
	private final ProductService self;

	public ProductService(ProductRepository productRepository, ProductMappingRepository productMappingRepository,
			@org.springframework.context.annotation.Lazy ProductService self) {
		this.productRepository = productRepository;
		this.productMappingRepository = productMappingRepository;
		this.self = self != null ? self : this;
	}

	@Transactional
	public ProductMapping saveMapping(ProductMapping mapping) {
		return productMappingRepository.save(mapping);
	}

	public Optional<ProductMapping> findMapping(String supermarket, String externalId) {
		return productMappingRepository.findBySupermarketAndExternalProductId(supermarket, externalId);
	}

	@Transactional
	public void deleteMapping(Long id) {
		Optional<ProductMapping> mappingOpt = productMappingRepository.findById(id);
		if (mappingOpt.isPresent()) {
			ProductMapping deleted = mappingOpt.get();
			productMappingRepository.delete(deleted);

			// Re-sequence remaining mappings
			List<ProductMapping> remaining = productMappingRepository.findBySearchTextAndSupermarketOrderByPriorityAsc(
					deleted.getSearchText(), deleted.getSupermarket());
			for (int i = 0; i < remaining.size(); i++) {
				ProductMapping m = remaining.get(i);
				m.setPriority(i);
				if (i == 0) {
					m.setFallbackForId(null);
				} else {
					m.setFallbackForId(remaining.get(0).getId());
				}
				productMappingRepository.save(m);
			}
		}
	}

	@Transactional
	public Product findOrCreateProduct(String externalId, String supermarket, String name, String brand, String url,
			String category) {
		return productRepository.findByExternalIdAndSupermarket(externalId, supermarket).orElseGet(() -> {
			Product newProduct = new Product(externalId, supermarket, name, brand, url, category);
			return productRepository.save(newProduct);
		});
	}

	public List<ProductMapping> findMappingsBySearchTextAndSupermarket(String searchText, String supermarket) {
		return productMappingRepository.findBySearchTextAndSupermarketOrderByPriorityAsc(searchText, supermarket);
	}

	public Optional<ProductMapping> findMappingById(Long id) {
		return productMappingRepository.findById(id);
	}

	public List<ProductMapping> findAllMappings() {
		return productMappingRepository.findAll();
	}

	@Transactional
	public void saveMapping(String query, String supermarket, SearchResult result) {
		self.findOrCreateProduct(result.externalId(), supermarket, result.name(), result.brand(), result.url(),
				result.category());

		ProductMapping mapping = new ProductMapping(query, supermarket, result.externalId(), result.name());
		self.saveMapping(mapping);
	}

	@Transactional
	public void saveMappingWithPriority(String query, String supermarket, SearchResult result, int priority) {
		self.findOrCreateProduct(result.externalId(), supermarket, result.name(), result.brand(), result.url(),
				result.category());

		List<ProductMapping> existing = productMappingRepository.findBySearchTextAndSupermarketOrderByPriorityAsc(query,
				supermarket);

		boolean alreadyExists = existing.stream().anyMatch(m -> m.getExternalProductId().equals(result.externalId()));
		if (alreadyExists) {
			log.info("Mapping already exists for query '{}' and SKU '{}'. Skipping duplicate save.", query,
					result.externalId());
			return;
		}

		ProductMapping mapping = new ProductMapping(query, supermarket, result.externalId(), result.name());
		mapping.setPriority(priority);
		if (priority > 0 && !existing.isEmpty()) {
			mapping.setFallbackForId(existing.get(0).getId());
		}
		self.saveMapping(mapping);
	}

	@Transactional
	public void promoteMapping(Long id) {
		Optional<ProductMapping> targetOpt = productMappingRepository.findById(id);
		if (targetOpt.isEmpty()) {
			return;
		}
		ProductMapping target = targetOpt.get();
		int currentPriority = target.getPriority();
		if (currentPriority <= 0) {
			return; // Already primary
		}

		List<ProductMapping> mappings = productMappingRepository
				.findBySearchTextAndSupermarketOrderByPriorityAsc(target.getSearchText(), target.getSupermarket());

		ProductMapping above = null;
		for (ProductMapping m : mappings) {
			if (m.getPriority() == currentPriority - 1) {
				above = m;
				break;
			}
		}

		if (above != null) {
			target.setPriority(currentPriority - 1);
			above.setPriority(currentPriority);

			productMappingRepository.save(target);
			productMappingRepository.save(above);

			// Re-sequence to prevent gaps and fix fallbackForId
			List<ProductMapping> updated = productMappingRepository
					.findBySearchTextAndSupermarketOrderByPriorityAsc(target.getSearchText(), target.getSupermarket());
			for (int i = 0; i < updated.size(); i++) {
				ProductMapping m = updated.get(i);
				m.setPriority(i);
				if (i == 0) {
					m.setFallbackForId(null);
				} else {
					m.setFallbackForId(updated.get(0).getId());
				}
				productMappingRepository.save(m);
			}
		}
	}

	@Transactional
	public void demoteMapping(Long id) {
		Optional<ProductMapping> targetOpt = productMappingRepository.findById(id);
		if (targetOpt.isEmpty()) {
			return;
		}
		ProductMapping target = targetOpt.get();
		int currentPriority = target.getPriority();

		List<ProductMapping> mappings = productMappingRepository
				.findBySearchTextAndSupermarketOrderByPriorityAsc(target.getSearchText(), target.getSupermarket());

		if (currentPriority >= mappings.size() - 1) {
			return; // Already at the bottom
		}

		ProductMapping below = null;
		for (ProductMapping m : mappings) {
			if (m.getPriority() == currentPriority + 1) {
				below = m;
				break;
			}
		}

		if (below != null) {
			target.setPriority(currentPriority + 1);
			below.setPriority(currentPriority);

			productMappingRepository.save(target);
			productMappingRepository.save(below);

			// Re-sequence to prevent gaps and fix fallbackForId
			List<ProductMapping> updated = productMappingRepository
					.findBySearchTextAndSupermarketOrderByPriorityAsc(target.getSearchText(), target.getSupermarket());
			for (int i = 0; i < updated.size(); i++) {
				ProductMapping m = updated.get(i);
				m.setPriority(i);
				if (i == 0) {
					m.setFallbackForId(null);
				} else {
					m.setFallbackForId(updated.get(0).getId());
				}
				productMappingRepository.save(m);
			}
		}
	}
}
