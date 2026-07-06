package com.autobuy.web.dto;

import com.autobuy.model.SearchResult;

public record ResolutionResult(SearchResult product, boolean saveMapping) {
}
