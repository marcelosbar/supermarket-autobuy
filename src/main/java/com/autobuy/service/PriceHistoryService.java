package com.autobuy.service;

import com.autobuy.model.PriceHistory;
import com.autobuy.model.Product;
import com.autobuy.model.SearchResult;
import com.autobuy.repository.PriceHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Service to manage price history logging.
 */
@Service
public class PriceHistoryService {

	private final PriceHistoryRepository priceHistoryRepository;
	private final ProductService productService;
	private final PriceHistoryService self;

	public PriceHistoryService(PriceHistoryRepository priceHistoryRepository, ProductService productService,
			@org.springframework.context.annotation.Lazy PriceHistoryService self) {
		this.priceHistoryRepository = priceHistoryRepository;
		this.productService = productService;
		this.self = self != null ? self : this;
	}

	@Transactional
	public PriceHistory logPrice(Product product, BigDecimal price, LocalDateTime timestamp) {
		PriceHistory history = new PriceHistory(product, price, timestamp, "SCRAPE");
		return priceHistoryRepository.save(history);
	}

	@Transactional
	public void logPrice(SearchResult result, String supermarket) {
		Product product = productService.findOrCreateProduct(result.externalId(), supermarket, result.name(),
				result.brand(), result.url(), result.category());
		self.logPrice(product, result.price(), LocalDateTime.now(ZoneId.systemDefault()));
	}
}
