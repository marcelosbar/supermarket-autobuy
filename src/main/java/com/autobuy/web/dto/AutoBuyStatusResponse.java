package com.autobuy.web.dto;

import com.autobuy.model.SearchResult;
import com.autobuy.web.AutoBuyWebService.AutoBuyState;
import java.util.List;

public record AutoBuyStatusResponse(AutoBuyState state, String currentItemQuery, int currentItemQuantity,
		List<SearchResult> searchResults, List<String> logs, String error) {
}
