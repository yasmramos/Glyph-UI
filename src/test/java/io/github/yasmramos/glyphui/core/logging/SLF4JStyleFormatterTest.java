package io.github.yasmramos.glyphui.core.logging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.logging.Level;
import java.util.logging.LogRecord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Characterization tests for the opt-in JUL formatter shipped as part of the
 * public {@code core.logging} package. It had no coverage at all, yet its
 * output is what users see once they point
 * {@code -Djava.util.logging.config.file} at {@code logging.properties}.
 */
class SLF4JStyleFormatterTest {

    private final SLF4JStyleFormatter formatter = new SLF4JStyleFormatter();

    private static LogRecord record(Level level, String logger, String message) {
        LogRecord record = new LogRecord(level, message);
        record.setLoggerName(logger);
        record.setMillis(0L);
        return record;
    }

    @Test
    @DisplayName("format renders time, thread, padded level, logger and message")
    void rendersCoreColumns() {
        String line = formatter.format(record(Level.INFO, "app.core", "started"));

        assertTrue(line.startsWith("00:00:00.000 "), "millis=0 renders as 00:00:00.000");
        assertTrue(line.contains("[" + Thread.currentThread().getName() + "]"),
                "the emitting thread name is included");
        assertTrue(line.contains("INFO "), "INFO is padded to five characters");
        assertTrue(line.contains("app.core - started"), "logger name precedes the message");
        assertTrue(line.endsWith(System.lineSeparator()), "records end with a line separator");
    }

    @Test
    @DisplayName("JUL levels are translated to their SLF4J tokens")
    void mapsLevelsToSlf4jTokens() {
        assertEquals("ERROR", levelToken(formatter.format(record(Level.SEVERE, "l", "m"))));
        assertEquals("WARN", levelToken(formatter.format(record(Level.WARNING, "l", "m"))));
        assertEquals("INFO", levelToken(formatter.format(record(Level.INFO, "l", "m"))));
        assertEquals("CONFIG", levelToken(formatter.format(record(Level.CONFIG, "l", "m"))));
        assertEquals("DEBUG", levelToken(formatter.format(record(Level.FINE, "l", "m"))));
        assertEquals("TRACE", levelToken(formatter.format(record(Level.FINER, "l", "m"))));
        assertEquals("TRACE", levelToken(formatter.format(record(Level.FINEST, "l", "m"))));
    }

    @Test
    @DisplayName("an unknown level name is passed through unchanged")
    void passesUnknownLevelThrough() {
        Level custom = new Level("NOTICE", 850) { };
        String line = formatter.format(record(custom, "l", "m"));

        assertTrue(line.contains("NOTICE "), "custom levels keep their own token");
    }

    @Test
    @DisplayName("a token longer than five characters is not truncated")
    void doesNotTruncateLongTokens() {
        Level custom = new Level("VERBOSE", 850) { };
        String line = formatter.format(record(custom, "l", "m"));

        assertTrue(line.contains("VERBOSE "), "padding never shortens a token");
    }

    @Test
    @DisplayName("an attached throwable is appended as a stack trace")
    void appendsThrowableStackTrace() {
        LogRecord record = record(Level.SEVERE, "app", "boom");
        record.setThrown(new IllegalStateException("kaboom"));

        String line = formatter.format(record);

        assertTrue(line.contains("java.lang.IllegalStateException: kaboom"),
                "the throwable type and message appear");
        assertTrue(line.contains("\tat "), "the stack frames are printed");
    }

    @Test
    @DisplayName("messages are formatted through the record's message pattern")
    void formatsParameterisedMessages() {
        LogRecord record = record(Level.INFO, "app", "hello {0}");
        record.setParameters(new Object[] {"world"});

        assertTrue(formatter.format(record).contains("app - hello world"),
                "formatMessage resolves {0} placeholders");
    }

    /** Extracts the padded level token that follows the {@code [thread]} field. */
    private static String levelToken(String line) {
        return line.substring(line.indexOf("] ") + 2).split(" ", 2)[0];
    }
}
