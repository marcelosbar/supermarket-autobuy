package com.autobuy.web;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Custom Logback appender that stores application logs in memory to be read by
 * the Web UI.
 */
public class MemoryAppender extends AppenderBase<ILoggingEvent> {

	private static final List<String> logs = new CopyOnWriteArrayList<>();

	static {
		try {
			LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
			MemoryAppender appender = new MemoryAppender();
			appender.setContext(context);
			appender.setName("WEB_MEMORY_APPENDER");
			appender.start();

			// Add appender to the autobuy root logger package
			ch.qos.logback.classic.Logger rootLogger = context.getLogger("com.autobuy");
			rootLogger.addAppender(appender);
		} catch (Exception e) {
			System.err.println("Failed to initialize Web MemoryAppender: " + e.getMessage());
		}
	}

	public static List<String> getLogs() {
		return logs;
	}

	public static void clear() {
		logs.clear();
	}

	@Override
	protected void append(ILoggingEvent eventObject) {
		String formatted = String.format("%s - %s", eventObject.getLevel().toString(),
				eventObject.getFormattedMessage());
		logs.add(formatted);
		if (logs.size() > 500) {
			logs.remove(0);
		}
	}
}
