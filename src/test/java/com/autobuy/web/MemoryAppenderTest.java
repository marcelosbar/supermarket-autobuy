package com.autobuy.web;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MemoryAppenderTest {

	@BeforeEach
	void setUp() {
		MemoryAppender.clear();
	}

	@Test
	void testAppendAndGetLogs() {
		ILoggingEvent event = mock(ILoggingEvent.class);
		when(event.getLevel()).thenReturn(Level.INFO);
		when(event.getFormattedMessage()).thenReturn("Test log message");

		MemoryAppender appender = new MemoryAppender();
		appender.append(event);

		List<String> logs = MemoryAppender.getLogs();
		assertEquals(1, logs.size());
		assertEquals("INFO - Test log message", logs.get(0));
	}

	@Test
	void testClearLogs() {
		ILoggingEvent event = mock(ILoggingEvent.class);
		when(event.getLevel()).thenReturn(Level.DEBUG);
		when(event.getFormattedMessage()).thenReturn("Debug message");

		MemoryAppender appender = new MemoryAppender();
		appender.append(event);

		assertFalse(MemoryAppender.getLogs().isEmpty());
		MemoryAppender.clear();
		assertTrue(MemoryAppender.getLogs().isEmpty());
	}

	@Test
	void testMaxSizeLimit() {
		MemoryAppender appender = new MemoryAppender();

		for (int i = 1; i <= 505; i++) {
			ILoggingEvent event = mock(ILoggingEvent.class);
			when(event.getLevel()).thenReturn(Level.INFO);
			when(event.getFormattedMessage()).thenReturn("Message " + i);
			appender.append(event);
		}

		List<String> logs = MemoryAppender.getLogs();
		assertEquals(500, logs.size());
		// The first 5 messages (1 to 5) should have been discarded due to the 500
		// limit.
		assertEquals("INFO - Message 6", logs.get(0));
		assertEquals("INFO - Message 505", logs.get(499));
	}
}
