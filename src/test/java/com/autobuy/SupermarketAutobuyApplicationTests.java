package com.autobuy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Standard context loading tests.
 */
@SpringBootTest
@ActiveProfiles("test") // Enable the 'test' profile to exclude the CLI runner from executing
class SupermarketAutobuyApplicationTests {

	@Test
	void contextLoads() {
		// Test passes if context starts up successfully without throwing exceptions
	}
}
