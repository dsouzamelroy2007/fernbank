package com.mel.fernbank.ledger;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

/** Attaches a Logback {@link ListAppender} to one logger for the duration of a test. */
public final class LogCapture implements AutoCloseable {

	private final Logger logger;
	private final ListAppender<ILoggingEvent> appender;

	private LogCapture(Class<?> loggedClass) {
		logger = (Logger) LoggerFactory.getLogger(loggedClass);
		appender = new ListAppender<>();
		appender.start();
		logger.addAppender(appender);
	}

	public static LogCapture attachTo(Class<?> loggedClass) {
		return new LogCapture(loggedClass);
	}

	public boolean hasEventAtLevelContaining(Level level, String substring) {
		return appender.list.stream()
				.anyMatch(e -> e.getLevel() == level && e.getFormattedMessage().contains(substring));
	}

	@Override
	public void close() {
		logger.detachAppender(appender);
	}
}
