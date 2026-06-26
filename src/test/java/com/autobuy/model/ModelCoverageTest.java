package com.autobuy.model;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class ModelCoverageTest {

	@Test
	void testProductCoverage() {
		Product p = new Product();
		p.setId(1L);
		p.setExternalId("sku1");
		p.setSupermarket("SUP");
		p.setName("Name");
		p.setBrand("Brand");
		p.setUrl("url");
		p.setCategory("cat");

		assertEquals(1L, p.getId());
		assertEquals("sku1", p.getExternalId());
		assertEquals("SUP", p.getSupermarket());
		assertEquals("Name", p.getName());
		assertEquals("Brand", p.getBrand());
		assertEquals("url", p.getUrl());
		assertEquals("cat", p.getCategory());
		assertNotNull(p.toString());

		Product p2 = new Product("sku1", "SUP", "Name", "Brand", "url", "cat");
		assertEquals("sku1", p2.getExternalId());
	}

	@Test
	void testProductMappingCoverage() {
		ProductMapping pm = new ProductMapping();
		pm.setId(1L);
		pm.setSearchText("search");
		pm.setSupermarket("SUP");
		pm.setExternalProductId("sku1");
		pm.setProductName("Name");

		assertEquals(1L, pm.getId());
		assertEquals("search", pm.getSearchText());
		assertEquals("SUP", pm.getSupermarket());
		assertEquals("sku1", pm.getExternalProductId());
		assertEquals("Name", pm.getProductName());
		assertNotNull(pm.toString());

		ProductMapping pm2 = new ProductMapping("search", "SUP", "sku1", "Name");
		assertEquals("search", pm2.getSearchText());
	}

	@Test
	void testPriceHistoryCoverage() {
		Product p = new Product();
		LocalDateTime now = LocalDateTime.now();
		PriceHistory ph = new PriceHistory();
		ph.setId(1L);
		ph.setProduct(p);
		ph.setPrice(BigDecimal.TEN);
		ph.setRecordedAt(now);
		ph.setSource("SRC");

		assertEquals(1L, ph.getId());
		assertEquals(p, ph.getProduct());
		assertEquals(BigDecimal.TEN, ph.getPrice());
		assertEquals(now, ph.getRecordedAt());
		assertEquals("SRC", ph.getSource());
		assertNotNull(ph.toString());

		PriceHistory ph2 = new PriceHistory(p, BigDecimal.TEN, now, "SRC");
		assertEquals(BigDecimal.TEN, ph2.getPrice());
	}

	@Test
	void testSearchResultAndShoppingItemCoverage() {
		SearchResult sr = new SearchResult("sku1", "Name", "Brand", BigDecimal.TEN, "url", "cat");
		assertEquals("sku1", sr.externalId());
		assertEquals("Name", sr.name());
		assertEquals("Brand", sr.brand());
		assertEquals(BigDecimal.TEN, sr.price());
		assertEquals("url", sr.url());
		assertEquals("cat", sr.category());

		ShoppingItem si = new ShoppingItem("query", 5);
		assertEquals("query", si.query());
		assertEquals(5, si.quantity());
	}
}
