package com.autobuy.service;

import com.autobuy.model.PriceHistory;
import com.autobuy.model.Product;
import com.autobuy.model.SearchResult;
import com.autobuy.repository.PriceHistoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PriceHistoryServiceTest {

	@Mock
	private PriceHistoryRepository priceHistoryRepository;

	@Mock
	private ProductService productService;

	@InjectMocks
	private PriceHistoryService priceHistoryService;

	@Test
	void testLogPrice() {
		Product product = new Product("SKU-LOG", "CONTINENTE", "Log Product", "Brand", "http://url", "Cat");
		product.setId(1L);

		LocalDateTime now = LocalDateTime.now();
		BigDecimal price = new BigDecimal("2.99");

		PriceHistory mockHistory = new PriceHistory(product, price, now, "SCRAPE");
		mockHistory.setId(10L);

		when(priceHistoryRepository.save(any(PriceHistory.class))).thenReturn(mockHistory);

		PriceHistory savedHistory = priceHistoryService.logPrice(product, price, now);

		assertNotNull(savedHistory);
		assertEquals(10L, savedHistory.getId());
		assertEquals(product.getId(), savedHistory.getProduct().getId());
		assertEquals(price, savedHistory.getPrice());
		assertEquals(now, savedHistory.getRecordedAt());
		assertEquals("SCRAPE", savedHistory.getSource());
		verify(priceHistoryRepository).save(any(PriceHistory.class));
	}

	@Test
	void testLogPrice_SearchResult() {
		SearchResult result = new SearchResult("SKU-2", "Name-2", "Brand-2", new BigDecimal("3.49"), "http://url-2",
				"Category-2");
		Product mockProduct = new Product("SKU-2", "CONTINENTE", "Name-2", "Brand-2", "http://url-2", "Category-2");
		mockProduct.setId(2L);

		when(productService.findOrCreateProduct("SKU-2", "CONTINENTE", "Name-2", "Brand-2", "http://url-2",
				"Category-2")).thenReturn(mockProduct);

		PriceHistory mockHistory = new PriceHistory(mockProduct, new BigDecimal("3.49"), LocalDateTime.now(), "SCRAPE");
		mockHistory.setId(20L);

		when(priceHistoryRepository.save(any(PriceHistory.class))).thenReturn(mockHistory);

		priceHistoryService.logPrice(result, "CONTINENTE");

		verify(productService).findOrCreateProduct("SKU-2", "CONTINENTE", "Name-2", "Brand-2", "http://url-2",
				"Category-2");
		verify(priceHistoryRepository).save(any(PriceHistory.class));
	}
}
