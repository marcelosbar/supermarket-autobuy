package com.autobuy.model;

/**
 * Result of product resolution.
 */
public record ResolveResult(SearchResult product, boolean alreadyAdded) {
}
