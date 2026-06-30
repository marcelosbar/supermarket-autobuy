package com.autobuy.service;

import com.autobuy.model.PriceHistory;
import com.autobuy.model.Product;
import com.autobuy.repository.PriceHistoryRepository;
import com.autobuy.repository.ProductMappingRepository;
import com.autobuy.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class PriceHistoryServiceTest {

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private ProductMappingRepository productMappingRepository;

	@Autowired
	private PriceHistoryRepository priceHistoryRepository;

	private PriceHistoryService priceHistoryService;

	@BeforeEach
	void setUp() {
		ProductService productService = new ProductService(productRepository, productMappingRepository);
		priceHistoryService = new PriceHistoryService(priceHistoryRepository, productService);
	}

	@Test
	void testLogPrice() {
		Product product = new Product("SKU-LOG", "CONTINENTE", "Log Product", "Brand", "http://url", "Cat");
		Product savedProduct = productRepository.save(product);

		LocalDateTime now = LocalDateTime.now();
		BigDecimal price = new BigDecimal("2.99");

		PriceHistory savedHistory = priceHistoryService.logPrice(savedProduct, price, now);

		assertNotNull(savedHistory.getId());
		assertEquals(savedProduct.getId(), savedHistory.getProduct().getId());
		assertEquals(price, savedHistory.getPrice());
		assertEquals(now, savedHistory.getRecordedAt());
		assertEquals("SCRAPE", savedHistory.getSource());

		List<PriceHistory> all = priceHistoryRepository.findAll();
		assertEquals(1, all.size());
		assertEquals(savedHistory.getId(), all.get(0).getId());
	}
}
