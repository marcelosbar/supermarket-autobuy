package com.autobuy.service;

import com.autobuy.driver.SupermarketDriver;
import com.autobuy.model.AutoBuyState;
import com.autobuy.model.SearchResult;
import com.autobuy.model.ShoppingItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class AutoBuyExecutionContextTest {

	@Mock
	private SupermarketDriver driver;

	private AutoBuyExecutionContext context;

	@BeforeEach
	void setUp() {
		context = new AutoBuyExecutionContext();
	}

	@Test
	void getState_defaultState_isIdle() {
		// Arrange & Act & Assert
		assertEquals(AutoBuyState.IDLE, context.getState());
		assertEquals("", context.getErrorMsg());
		assertTrue(context.getSkippedItems().isEmpty());
	}

	@Test
	void updateStateFailure_errorMessage_setsFailedStateAndMessage() {
		// Act
		context.updateStateFailure("Execution failed");

		// Assert
		assertEquals(AutoBuyState.FAILED, context.getState());
		assertEquals("Execution failed", context.getErrorMsg());
	}

	@Test
	void recordSkippedItem_multipleItems_appendsToSkippedItemsList() {
		// Act
		context.recordSkippedItem("itemA");
		context.recordSkippedItem("itemB");

		// Assert
		assertEquals(List.of("itemA", "itemB"), context.getSkippedItems());
	}

	@Test
	void reset_modifiedContext_resetsToDefaultRunningState() {
		// Arrange
		context.transitionTo(AutoBuyState.FAILED);
		context.setCurrentItemQuery("apples");
		context.setCurrentItemQuantity(3);
		context.setSearchResults(List.of(new SearchResult("sku", "name", "brand", BigDecimal.ONE, "url", "cat")));
		context.recordSkippedItem("skipped");
		context.getExhaustedItems().add(new ShoppingItem("exhausted", 1));
		context.setMappingInstructions("instruct");
		context.setBrowserOpen(true);

		// Act
		context.reset();

		// Assert
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
	void setActiveDriver_driverInstance_updatesBrowserOpenAndActiveDriver() {
		// Act
		context.setActiveDriver(driver);

		// Assert
		assertTrue(context.isBrowserOpen());
		assertEquals(driver, context.getActiveDriver());

		// Act
		context.setActiveDriver(null);

		// Assert
		assertFalse(context.isBrowserOpen());
		assertNull(context.getActiveDriver());
	}
}
