package com.autobuy.web;

import com.autobuy.service.ShutdownService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller exposing system administration endpoints.
 */
@RestController
@RequestMapping("/api")
public class SystemController {

	private static final Logger log = LoggerFactory.getLogger(SystemController.class);

	private final ShutdownService shutdownService;

	private static final String SUCCESS_KEY = "success";
	private static final String MESSAGE_KEY = "message";

	public SystemController(ShutdownService shutdownService) {
		this.shutdownService = shutdownService;
	}

	@PostMapping("/shutdown")
	public ResponseEntity<Map<String, Object>> shutdown() {
		log.info("Shutdown requested. Initiating graceful shutdown via ShutdownService...");
		shutdownService.initiateShutdown(1000); // 1 second delay
		Map<String, Object> response = new HashMap<>();
		response.put(SUCCESS_KEY, true);
		response.put(MESSAGE_KEY, "Application is shutting down gracefully. Backup will be created.");
		return ResponseEntity.ok(response);
	}
}
