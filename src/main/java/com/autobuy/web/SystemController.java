package com.autobuy.web;

import com.autobuy.service.ShutdownService;
import com.autobuy.web.dto.ActionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller exposing system administration endpoints.
 */
@RestController
@RequestMapping("/system")
public class SystemController {

	private static final Logger log = LoggerFactory.getLogger(SystemController.class);

	private final ShutdownService shutdownService;

	public SystemController(ShutdownService shutdownService) {
		this.shutdownService = shutdownService;
	}

	@PostMapping("/shutdown")
	public ResponseEntity<ActionResponse> shutdown() {
		log.info("Shutdown requested. Initiating graceful shutdown via ShutdownService...");
		shutdownService.initiateShutdown(1000); // 1 second delay
		return ResponseEntity
				.ok(new ActionResponse(true, "Application is shutting down gracefully. Backup will be created."));
	}
}
