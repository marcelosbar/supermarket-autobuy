package com.autobuy.service;

import com.autobuy.driver.SupermarketDriver;
import com.autobuy.model.SearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import jakarta.annotation.PreDestroy;

import java.util.List;

/**
 * Service to handle unauthenticated searches using a separate driver lifecycle.
 */
@Service
public class GuestSearchService {

	private static final Logger log = LoggerFactory.getLogger(GuestSearchService.class);

	private final List<SupermarketDriver> drivers;
	private SupermarketDriver guestSearchDriver = null;

	public GuestSearchService(List<SupermarketDriver> drivers) {
		this.drivers = drivers;
	}

	public synchronized List<SearchResult> performGuestSearch(String query, String supermarket) {
		if (guestSearchDriver == null) {
			SupermarketDriver driver = drivers.stream()
					.filter(d -> d.getSupermarketName().equalsIgnoreCase(supermarket)).findFirst().orElse(null);
			if (driver == null) {
				throw new IllegalArgumentException("No driver found for supermarket: " + supermarket);
			}
			guestSearchDriver = driver;
			String sanitizedSupermarket = supermarket.replace('\n', '_').replace('\r', '_');
			log.info("Initializing guest search driver for {}...", sanitizedSupermarket);
			guestSearchDriver.initialize(null, null, false);
		}

		try {
			return guestSearchDriver.searchProduct(query);
		} catch (Exception e) {
			close();
			throw new com.autobuy.exception.AutoBuyException("Guest search failed", e);
		}
	}

	public synchronized void close() {
		if (guestSearchDriver != null) {
			try {
				log.info("Closing guest search driver...");
				guestSearchDriver.close();
			} catch (Exception e) {
				log.error("Error closing guest search driver", e);
			}
			guestSearchDriver = null;
		}
	}

	@PreDestroy
	public synchronized void shutdown() {
		close();
	}
}
