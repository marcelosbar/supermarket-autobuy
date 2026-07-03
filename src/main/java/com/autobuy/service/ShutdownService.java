package com.autobuy.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Service;

/**
 * Service to handle programmatically shutting down the Spring context.
 */
@Service
public class ShutdownService {

	private static final Logger log = LoggerFactory.getLogger(ShutdownService.class);

	private final ConfigurableApplicationContext context;

	public ShutdownService(ConfigurableApplicationContext context) {
		this.context = context;
	}

	/**
	 * Initiates a graceful shutdown of the Spring Application Context.
	 *
	 * @param delayMs
	 *            The delay in milliseconds before triggering the shutdown.
	 */
	public void initiateShutdown(long delayMs) {
		log.info("Shutdown requested. Server will stop in {} ms...", delayMs);
		Thread.ofVirtual().start(() -> {
			try {
				Thread.sleep(delayMs);
			} catch (InterruptedException _) {
				Thread.currentThread().interrupt();
			}
			log.info("Closing application context programmatically...");
			context.close();
			exit(0);
		});
	}

	void exit(int status) {
		System.exit(status);
	}
}
