package com.autobuy.service;

import com.autobuy.model.SearchResult;

/**
 * Result of product resolution.
 */
public record ResolveResult(SearchResult product, boolean alreadyAdded) {
}
