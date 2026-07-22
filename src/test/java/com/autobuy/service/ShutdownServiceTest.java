package com.autobuy.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ConfigurableApplicationContext;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class ShutdownServiceTest {

	@Mock
	private ConfigurableApplicationContext context;

	@Test
	void initiateShutdown_delayMs_closesContextAndExits() throws InterruptedException {
		// Arrange
		CountDownLatch latch = new CountDownLatch(1);

		ShutdownService service = new ShutdownService(context) {
			@Override
			void exit(int status) {
				assertEquals(0, status);
				latch.countDown();
			}
		};

		// Act
		service.initiateShutdown(10);

		// Assert
		assertTrue(latch.await(2, TimeUnit.SECONDS), "Shutdown should close context and exit");
		verify(context).close();
	}
}
