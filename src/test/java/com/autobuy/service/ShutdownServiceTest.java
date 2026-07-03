package com.autobuy.service;

import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class ShutdownServiceTest {

	@Test
	void testInitiateShutdown() throws InterruptedException {
		ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
		CountDownLatch latch = new CountDownLatch(1);

		ShutdownService service = new ShutdownService(context) {
			@Override
			void exit(int status) {
				assertEquals(0, status);
				latch.countDown();
			}
		};

		service.initiateShutdown(10);

		assertTrue(latch.await(2, TimeUnit.SECONDS), "Shutdown should close context and exit");
		verify(context).close();
	}
}
