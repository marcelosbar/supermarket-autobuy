package com.autobuy.service;

import com.autobuy.driver.SupermarketDriver;
import com.autobuy.model.AutoBuyState;
import com.autobuy.model.SearchResult;
import com.autobuy.model.ShoppingItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class AutoBuyExecutionContextTest {

	private AutoBuyExecutionContext context;

	@BeforeEach
	void setUp() {
		context = new AutoBuyExecutionContext();
	}

	@Test
	void testIdleByDefault() {
		assertEquals(AutoBuyState.IDLE, context.getState());
		assertEquals("", context.getErrorMsg());
		assertTrue(context.getSkippedItems().isEmpty());
	}

	@Test
	void testUpdateStateFailure() {
		context.updateStateFailure("Execution failed");
		assertEquals(AutoBuyState.FAILED, context.getState());
		assertEquals("Execution failed", context.getErrorMsg());
	}

	@Test
	void testRecordSkippedItem() {
		context.recordSkippedItem("itemA");
		context.recordSkippedItem("itemB");
		assertEquals(List.of("itemA", "itemB"), context.getSkippedItems());
	}

	@Test
	void testReset() {
		context.transitionTo(AutoBuyState.FAILED);
		context.setCurrentItemQuery("apples");
		context.setCurrentItemQuantity(3);
		context.setSearchResults(List.of(new SearchResult("sku", "name", "brand", BigDecimal.ONE, "url", "cat")));
		context.recordSkippedItem("skipped");
		context.getExhaustedItems().add(new ShoppingItem("exhausted", 1));
		context.setMappingInstructions("instruct");
		context.setBrowserOpen(true);

		context.reset();

		assertEquals(AutoBuyState.RUNNING, context.getState());
		assertEquals("", context.getCurrentItemQuery());
		assertEquals(0, context.getCurrentItemQuantity());
		assertTrue(context.getSearchResults().isEmpty());
		assertTrue(context.getSkippedItems().isEmpty());
		assertTrue(context.getExhaustedItems().isEmpty());
		assertEquals("", context.getMappingInstructions());
		assertFalse(context.isBrowserOpen());
	}

	@Test
	void testActiveDriverAndBrowserOpen() {
		SupermarketDriver driver = mock(SupermarketDriver.class);
		context.setActiveDriver(driver);
		assertTrue(context.isBrowserOpen());
		assertEquals(driver, context.getActiveDriver());

		context.setActiveDriver(null);
		assertFalse(context.isBrowserOpen());
		assertNull(context.getActiveDriver());
	}
}
