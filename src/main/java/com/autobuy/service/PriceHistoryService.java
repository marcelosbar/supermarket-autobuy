package com.autobuy.service;

import com.autobuy.model.PriceHistory;
import com.autobuy.model.Product;
import com.autobuy.repository.PriceHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Service to manage price history logging.
 */
@Service
public class PriceHistoryService {

	private final PriceHistoryRepository priceHistoryRepository;

	public PriceHistoryService(PriceHistoryRepository priceHistoryRepository) {
		this.priceHistoryRepository = priceHistoryRepository;
	}

	@Transactional
	public PriceHistory logPrice(Product product, BigDecimal price, LocalDateTime timestamp) {
		PriceHistory history = new PriceHistory(product, price, timestamp, "SCRAPE");
		return priceHistoryRepository.save(history);
	}
}
