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

	private final ProductRepository productRepository;
	private final ProductMappingRepository productMappingRepository;

	public ProductService(ProductRepository productRepository, ProductMappingRepository productMappingRepository) {
		this.productRepository = productRepository;
		this.productMappingRepository = productMappingRepository;
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
		productMappingRepository.deleteById(id);
	}

	@Transactional
	public Product findOrCreateProduct(String externalId, String supermarket, String name, String brand, String url,
			String category) {
		return productRepository.findByExternalIdAndSupermarket(externalId, supermarket).orElseGet(() -> {
			Product newProduct = new Product(externalId, supermarket, name, brand, url, category);
			return productRepository.save(newProduct);
		});
	}

	public Optional<ProductMapping> findMappingBySearchTextAndSupermarket(String searchText, String supermarket) {
		return productMappingRepository.findBySearchTextAndSupermarket(searchText, supermarket);
	}

	public Optional<ProductMapping> findMappingById(Long id) {
		return productMappingRepository.findById(id);
	}

	public List<ProductMapping> findAllMappings() {
		return productMappingRepository.findAll();
	}

	@Transactional
	public void saveMapping(String query, String supermarket, SearchResult result) {
		findOrCreateProduct(result.externalId(), supermarket, result.name(), result.brand(), result.url(),
				result.category());

		ProductMapping mapping = new ProductMapping(query, supermarket, result.externalId(), result.name());
		saveMapping(mapping);
	}
}
