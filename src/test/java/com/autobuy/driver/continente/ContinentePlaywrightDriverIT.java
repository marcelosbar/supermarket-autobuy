package com.autobuy.driver.continente;

import com.autobuy.model.SearchResult;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ContinentePlaywrightDriverIT {

	@Test
	void searchProduct_validQuery_returnsBrandAndUrl() {
		// Arrange
		ContinentePlaywrightDriver driver = new ContinentePlaywrightDriver();
		try {
			driver.initialize(null, null, false);

			// Act
			List<SearchResult> results = driver.searchProduct("Doce de Leite Continente Seleção");

			// Assert
			assertFalse(results.isEmpty(), "Should find at least one search result");
			SearchResult first = results.get(0);

			// Assert brand is correctly parsed
			assertTrue(first.brand().toLowerCase().contains("continente"),
					"Brand should contain 'Continente', but got: " + first.brand());

			// Assert url points to product page and not homepage
			assertTrue(first.url().contains("/produto/"), "URL should contain '/produto/', but got: " + first.url());
			assertNotEquals("https://www.continente.pt", first.url(), "URL should not point to homepage");
		} finally {
			driver.close();
		}
	}
}
