package dev.schemtocreate.cli;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.OutputStreamAppender;
import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Applies {@code --verbose} / {@code --debug} / {@code --quiet} to the logging backend.
 *
 * <p>Reaches into Logback deliberately: the level has to change after the command line is
 * parsed, which a static {@code logback.xml} cannot express. If some other backend is on
 * the classpath the calls degrade to no-ops rather than failing.
 */
final class LoggingConfigurator {

    private static final String DETAILED_PATTERN = "%d{HH:mm:ss.SSS} %-5level %logger{28} - %msg%n";

    private LoggingConfigurator() {
    }

    static void apply(boolean debug, boolean quiet) {
        ILoggerFactory factory = LoggerFactory.getILoggerFactory();
        if (!(factory instanceof LoggerContext context)) {
            return;
        }
        ch.qos.logback.classic.Logger root = context.getLogger(Logger.ROOT_LOGGER_NAME);
        root.setLevel(debug ? Level.DEBUG : quiet ? Level.WARN : Level.INFO);
        if (debug) {
            applyDetailedPattern(context, root);
        }
    }

    /** Debug output is worth timestamps and logger names; normal output is not. */
    private static void applyDetailedPattern(LoggerContext context,
                                             ch.qos.logback.classic.Logger root) {
        Appender<ILoggingEvent> appender = root.getAppender("CONSOLE");
        if (!(appender instanceof OutputStreamAppender<ILoggingEvent> stream)) {
            return;
        }
        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setContext(context);
        encoder.setPattern(DETAILED_PATTERN);
        encoder.start();
        stream.stop();
        stream.setEncoder(encoder);
        stream.start();
    }
}
