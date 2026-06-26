package com.autobuy.driver;

import com.autobuy.model.SearchResult;
import com.microsoft.playwright.Locator;
import org.junit.jupiter.api.Test;

import java.util.List;

class ContinentePlaywrightDriverDiagnosticTest {

	@Test
	void runDiagnostic() {
		System.out.println("Starting Playwright search diagnostic run using production driver...");
		ContinentePlaywrightDriver driver = new ContinentePlaywrightDriver();
		try {
			// Initialize in guest mode (headless)
			driver.initialize("", "", false);

			// Test search queries
			String[] queries = {"Leite Meio Gordo Mimosa 1L", "Ovos Classe L Continente",
					"Arroz Carolino Continente 1kg", "Esparguete Continente 500g", "Açúcar Branco Continente 1kg"};

			for (String query : queries) {
				System.out.println("\n-------------------------------------------");
				System.out.println("Searching for: " + query);
				List<SearchResult> results = driver.searchProduct(query);
				System.out.println("Results count: " + results.size());
				for (int i = 0; i < results.size(); i++) {
					SearchResult res = results.get(i);
					System.out.printf("[%d] SKU: %s, Name: '%s', Price: %s\n", i + 1, res.externalId(), res.name(),
							res.price());
					Locator tile = driver.getPage()
							.locator(String.format("div.product[data-pid='%s'], div.product-tile[data-pid='%s']",
									res.externalId(), res.externalId()))
							.first();
					if (tile.count() > 0) {
						System.out.println("  Quantity selector details:");
						String qtyDetails = (String) tile.evaluate("el => {"
								+ "  const qtyDiv = el.querySelector('div.quantity-update, div.product-qty-vue, [class*=\"quantity-update\"]');"
								+ "  if (!qtyDiv) return 'Not found';"
								+ "  const childs = qtyDiv.querySelectorAll('*');" + "  const childInfo = [];"
								+ "  for (let i = 0; i < childs.length; i++) {"
								+ "    childInfo.push(childs[i].tagName + ' (class=' + (childs[i].className || '') + '): ' + childs[i].innerText);"
								+ "  }" + "  return childInfo.join(' | ');" + "}");
						System.out.println("    " + qtyDetails);
					}
				}
			}

		} catch (Exception e) {
			System.err.println("Diagnostic failed: " + e.getMessage());
			e.printStackTrace();
		} finally {
			try {
				driver.close();
			} catch (Exception ignored) {
			}
		}
	}
}
