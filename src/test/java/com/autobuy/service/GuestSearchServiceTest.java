package com.autobuy.service;

import com.autobuy.driver.SupermarketDriver;
import com.autobuy.model.SearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GuestSearchServiceTest {

	private SupermarketDriver supermarketDriver;
	private GuestSearchService service;

	@BeforeEach
	void setUp() {
		supermarketDriver = mock(SupermarketDriver.class);
		when(supermarketDriver.getSupermarketName()).thenReturn("CONTINENTE");
		service = new GuestSearchService(List.of(supermarketDriver));
	}

	@Test
	void testPerformGuestSearch_CachesDriver() {
		List<SearchResult> expected = List.of(new SearchResult("sku1", "Test", "Brand", BigDecimal.ONE, "url", "cat"));
		when(supermarketDriver.searchProduct("query")).thenReturn(expected);

		// First call: should initialize and search
		List<SearchResult> results1 = service.performGuestSearch("query", "CONTINENTE");
		assertEquals(expected, results1);
		verify(supermarketDriver, times(1)).initialize(null, null, false);
		verify(supermarketDriver, times(1)).searchProduct("query");

		// Second call: should reuse cached driver without re-initializing
		List<SearchResult> results2 = service.performGuestSearch("query", "CONTINENTE");
		assertEquals(expected, results2);
		verify(supermarketDriver, times(1)).initialize(null, null, false); // still 1
		verify(supermarketDriver, times(2)).searchProduct("query");
	}

	@Test
	void testPerformGuestSearch_SelfHealsOnFailure() {
		when(supermarketDriver.searchProduct("fail")).thenThrow(new RuntimeException("Search failed"));

		// Perform guest search and expect exception
		assertThrows(RuntimeException.class, () -> service.performGuestSearch("fail", "CONTINENTE"));
		verify(supermarketDriver, times(1)).close(); // Should be closed on error

		// Next guest search should recreate/re-initialize the driver
		List<SearchResult> expected = List.of(new SearchResult("sku1", "Test", "Brand", BigDecimal.ONE, "url", "cat"));
		when(supermarketDriver.searchProduct("ok")).thenReturn(expected);

		List<SearchResult> results = service.performGuestSearch("ok", "CONTINENTE");
		assertEquals(expected, results);
		verify(supermarketDriver, times(2)).initialize(null, null, false); // Initialized again
	}

	@Test
	void testPerformGuestSearch_NonexistentSupermarket() {
		assertThrows(IllegalArgumentException.class, () -> service.performGuestSearch("query", "NONEXISTENT"));
	}

	@Test
	void testShutdown_ClosesGuestSearchDriver() {
		service.performGuestSearch("query", "CONTINENTE");
		service.shutdown();
		verify(supermarketDriver, times(1)).close();
	}
}
