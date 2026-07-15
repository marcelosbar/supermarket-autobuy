package com.autobuy.service;

import com.autobuy.driver.SupermarketDriver;
import com.autobuy.model.AutoBuyState;
import com.autobuy.model.SearchResult;
import com.autobuy.model.ShoppingItem;
import com.autobuy.config.MemoryAppender;
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
		MemoryAppender.clear();
	}

	@Test
	void testGetStatusIdleByDefault() {
		var status = context.getStatus();
		assertEquals(AutoBuyState.IDLE, status.state());
		assertTrue(status.logs().isEmpty());
	}

	@Test
	void testUpdateStateFailure() {
		context.updateStateFailure("Execution failed");
		var status = context.getStatus();
		assertEquals(AutoBuyState.FAILED, status.state());
		assertEquals("Execution failed", status.error());
	}

	@Test
	void testRecordSkippedItem() {
		context.recordSkippedItem("itemA");
		context.recordSkippedItem("itemB");
		var status = context.getStatus();
		assertEquals(List.of("itemA", "itemB"), status.skippedItems());
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

		var status = context.getStatus();
		assertEquals(AutoBuyState.RUNNING, status.state());
		assertEquals("", status.currentItemQuery());
		assertEquals(0, status.currentItemQuantity());
		assertTrue(status.searchResults().isEmpty());
		assertTrue(status.skippedItems().isEmpty());
		assertTrue(status.exhaustedItems().isEmpty());
		assertEquals("", status.mappingInstructions());
		assertFalse(status.browserOpen());
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
