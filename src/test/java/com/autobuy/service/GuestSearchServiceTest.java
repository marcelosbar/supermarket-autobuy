package com.autobuy.service;

import com.autobuy.driver.SupermarketDriver;
import com.autobuy.model.SearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GuestSearchServiceTest {

	@Mock
	private SupermarketDriver supermarketDriver;

	private GuestSearchService service;

	@BeforeEach
	void setUp() {
		lenient().when(supermarketDriver.getSupermarketName()).thenReturn("CONTINENTE");
		service = new GuestSearchService(List.of(supermarketDriver));
	}

	@Test
	void performGuestSearch_multipleCalls_cachesAndReusesDriver() {
		// Arrange
		List<SearchResult> expected = List.of(new SearchResult("sku1", "Test", "Brand", BigDecimal.ONE, "url", "cat"));
		when(supermarketDriver.searchProduct("query")).thenReturn(expected);

		// Act (First call: should initialize and search)
		List<SearchResult> results1 = service.performGuestSearch("query", "CONTINENTE");

		// Assert
		assertEquals(expected, results1);
		verify(supermarketDriver, times(1)).initialize(null, null, false);
		verify(supermarketDriver, times(1)).searchProduct("query");

		// Act (Second call: should reuse cached driver without re-initializing)
		List<SearchResult> results2 = service.performGuestSearch("query", "CONTINENTE");

		// Assert
		assertEquals(expected, results2);
		verify(supermarketDriver, times(1)).initialize(null, null, false);
		verify(supermarketDriver, times(2)).searchProduct("query");
	}

	@Test
	void performGuestSearch_driverFailure_closesAndReinitializesOnNextCall() {
		// Arrange
		when(supermarketDriver.searchProduct("fail")).thenThrow(new RuntimeException("Search failed"));

		// Act & Assert (Perform guest search and expect exception)
		assertThrows(RuntimeException.class, () -> service.performGuestSearch("fail", "CONTINENTE"));
		verify(supermarketDriver, times(1)).close();

		// Arrange
		List<SearchResult> expected = List.of(new SearchResult("sku1", "Test", "Brand", BigDecimal.ONE, "url", "cat"));
		when(supermarketDriver.searchProduct("ok")).thenReturn(expected);

		// Act
		List<SearchResult> results = service.performGuestSearch("ok", "CONTINENTE");

		// Assert
		assertEquals(expected, results);
		verify(supermarketDriver, times(2)).initialize(null, null, false);
	}

	@Test
	void performGuestSearch_unknownSupermarket_throwsIllegalArgumentException() {
		// Arrange & Act & Assert
		assertThrows(IllegalArgumentException.class, () -> service.performGuestSearch("query", "NONEXISTENT"));
	}

	@Test
	void shutdown_activeGuestDriver_closesDriver() {
		// Arrange
		List<SearchResult> expected = List.of(new SearchResult("sku1", "Test", "Brand", BigDecimal.ONE, "url", "cat"));
		when(supermarketDriver.searchProduct("query")).thenReturn(expected);
		service.performGuestSearch("query", "CONTINENTE");

		// Act
		service.shutdown();

		// Assert
		verify(supermarketDriver, times(1)).close();
	}
}
