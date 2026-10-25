package io.github.yasmramos.glyphui.core.logging;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.ZoneId;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

/**
 * Formats JUL records the way SLF4J/logback print to the console: timestamp,
 * thread, right-padded level token, logger name and message, followed by the
 * stack trace of any attached throwable.
 *
 * <p>Levels are translated to their SLF4J equivalents ({@code SEVERE} to
 * {@code ERROR}, {@code WARNING} to {@code WARN}, {@code FINE} to
 * {@code DEBUG}, {@code FINER}/{@code FINEST} to {@code TRACE}). The class is
 * wired up through {@code src/main/resources/logging.properties}, which is only
 * honoured when the JVM is started with
 * {@code -Djava.util.logging.config.file} pointing at it.</p>
 */
public class SLF4JStyleFormatter extends Formatter {

    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    @Override
    public String format(LogRecord record) {
        StringBuilder sb = new StringBuilder(160);
        Instant instant = Instant.ofEpochMilli(record.getMillis());
        sb.append(TIME.format(instant)).append(" ");
        sb.append("[").append(Thread.currentThread().getName()).append("] ");
        sb.append(pad(level(record))).append(" ");
        sb.append(record.getLoggerName()).append(" - ");
        sb.append(formatMessage(record)).append(System.lineSeparator());

        if (record.getThrown() != null) {
            StringWriter sw = new StringWriter();
            record.getThrown().printStackTrace(new PrintWriter(sw));
            sb.append(sw);
        }
        return sb.toString();
    }

    private static String pad(String s) {
        return s.length() >= 5 ? s : s + " ".repeat(5 - s.length());
    }

    private static String level(LogRecord record) {
        return switch (record.getLevel().getName()) {
            case "SEVERE" -> "ERROR";
            case "WARNING" -> "WARN";
            case "INFO" -> "INFO";
            case "CONFIG" -> "CONFIG";
            case "FINE" -> "DEBUG";
            case "FINER", "FINEST" -> "TRACE";
            default -> record.getLevel().getName();
        };
    }
}
