package com.autobuy.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemoryAppenderTest {

	@Mock
	private ILoggingEvent loggingEvent;

	@BeforeEach
	void setUp() {
		MemoryAppender.clear();
	}

	@Test
	void append_validLoggingEvent_storesFormattedLog() {
		// Arrange
		when(loggingEvent.getLevel()).thenReturn(Level.INFO);
		when(loggingEvent.getFormattedMessage()).thenReturn("Test log message");
		MemoryAppender appender = new MemoryAppender();

		// Act
		appender.append(loggingEvent);

		// Assert
		List<String> logs = MemoryAppender.getLogs();
		assertEquals(1, logs.size());
		assertEquals("INFO - Test log message", logs.get(0));
	}

	@Test
	void clear_existingLogs_clearsAllLogs() {
		// Arrange
		when(loggingEvent.getLevel()).thenReturn(Level.DEBUG);
		when(loggingEvent.getFormattedMessage()).thenReturn("Debug message");
		MemoryAppender appender = new MemoryAppender();

		// Act
		appender.append(loggingEvent);

		// Assert
		assertFalse(MemoryAppender.getLogs().isEmpty());
		MemoryAppender.clear();
		assertTrue(MemoryAppender.getLogs().isEmpty());
	}

	@Test
	void append_exceedingMaxCapacity_retainsOnlyLatestLogs() {
		// Arrange
		MemoryAppender appender = new MemoryAppender();
		AtomicInteger counter = new AtomicInteger(1);
		when(loggingEvent.getLevel()).thenReturn(Level.INFO);
		when(loggingEvent.getFormattedMessage()).thenAnswer(invocation -> "Message " + counter.getAndIncrement());

		// Act
		for (int i = 1; i <= 505; i++) {
			appender.append(loggingEvent);
		}

		// Assert
		List<String> logs = MemoryAppender.getLogs();
		assertEquals(500, logs.size());
		assertEquals("INFO - Message 6", logs.get(0));
		assertEquals("INFO - Message 505", logs.get(499));
	}
}
