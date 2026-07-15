package com.autobuy.service;

import com.autobuy.driver.SupermarketDriver;
import com.autobuy.model.AutoBuyState;
import com.autobuy.model.SearchResult;
import com.autobuy.model.ShoppingItem;
import com.autobuy.config.MemoryAppender;
import com.autobuy.web.dto.AutoBuyStatusResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Thread-safe state holder for the Auto-Buy process.
 */
@Component
public class AutoBuyExecutionContext {

	private static final Logger log = LoggerFactory.getLogger(AutoBuyExecutionContext.class);

	private AutoBuyState state = AutoBuyState.IDLE;
	private String currentItemQuery = "";
	private int currentItemQuantity = 0;
	private final List<SearchResult> searchResults = new ArrayList<>();
	private String errorMsg = "";
	private final List<String> skippedItems = new ArrayList<>();
	private final List<ShoppingItem> exhaustedItems = new ArrayList<>();
	private String mappingInstructions = "";
	private boolean browserOpen = false;
	private SupermarketDriver activeDriver = null;

	public synchronized AutoBuyStatusResponse getStatus() {
		List<String> exhaustedQueries = exhaustedItems.stream().map(ShoppingItem::query).toList();
		return new AutoBuyStatusResponse(state, currentItemQuery, currentItemQuantity, new ArrayList<>(searchResults),
				new ArrayList<>(MemoryAppender.getLogs()), errorMsg, new ArrayList<>(skippedItems), exhaustedQueries,
				browserOpen, mappingInstructions);
	}

	public synchronized void updateStateFailure(String message) {
		this.state = AutoBuyState.FAILED;
		this.errorMsg = message;
		log.error("Auto-buy execution failed: {}", message);
	}

	public synchronized void recordSkippedItem(String query) {
		this.skippedItems.add(query);
	}

	public synchronized void reset() {
		this.state = AutoBuyState.RUNNING;
		this.errorMsg = "";
		this.currentItemQuery = "";
		this.currentItemQuantity = 0;
		this.searchResults.clear();
		this.skippedItems.clear();
		this.exhaustedItems.clear();
		this.mappingInstructions = "";
		this.browserOpen = false;
		MemoryAppender.clear();
	}

	public synchronized void transitionTo(AutoBuyState newState) {
		this.state = newState;
	}

	public synchronized AutoBuyState getState() {
		return state;
	}

	public synchronized String getCurrentItemQuery() {
		return currentItemQuery;
	}

	public synchronized void setCurrentItemQuery(String query) {
		this.currentItemQuery = query;
	}

	public synchronized int getCurrentItemQuantity() {
		return currentItemQuantity;
	}

	public synchronized void setCurrentItemQuantity(int quantity) {
		this.currentItemQuantity = quantity;
	}

	public synchronized List<SearchResult> getSearchResults() {
		return new ArrayList<>(searchResults);
	}

	public synchronized void setSearchResults(List<SearchResult> results) {
		this.searchResults.clear();
		this.searchResults.addAll(results);
	}

	public synchronized String getErrorMsg() {
		return errorMsg;
	}

	public synchronized void setErrorMsg(String errorMsg) {
		this.errorMsg = errorMsg;
	}

	public synchronized List<String> getSkippedItems() {
		return new ArrayList<>(skippedItems);
	}

	public synchronized List<ShoppingItem> getExhaustedItems() {
		return exhaustedItems;
	}

	public synchronized String getMappingInstructions() {
		return mappingInstructions;
	}

	public synchronized void setMappingInstructions(String instructions) {
		this.mappingInstructions = instructions;
	}

	public synchronized boolean isBrowserOpen() {
		return browserOpen;
	}

	public synchronized void setBrowserOpen(boolean open) {
		this.browserOpen = open;
	}

	public synchronized SupermarketDriver getActiveDriver() {
		return activeDriver;
	}

	public synchronized void setActiveDriver(SupermarketDriver driver) {
		this.activeDriver = driver;
		this.browserOpen = driver != null;
	}
}
