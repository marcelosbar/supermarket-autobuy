package com.autobuy.config;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Custom Logback appender that stores application logs in memory to be read by
 * the Web UI. Logback instantiates and registers this appender via
 * {@code logback-spring.xml} — no manual registration is needed.
 */
public class MemoryAppender extends AppenderBase<ILoggingEvent> {

	private static final int MAX_SIZE = 500;

	private static final ConcurrentLinkedDeque<String> logs = new ConcurrentLinkedDeque<>();

	public static List<String> getLogs() {
		return new ArrayList<>(logs);
	}

	public static void clear() {
		logs.clear();
	}

	@Override
	protected void append(ILoggingEvent eventObject) {
		String formatted = String.format("%s - %s", eventObject.getLevel().toString(),
				eventObject.getFormattedMessage());
		logs.addLast(formatted);
		if (logs.size() > MAX_SIZE) {
			logs.pollFirst();
		}
	}
}
