package de.malfrador.notion;

import notion.api.v1.logging.NotionLogger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The logger included with the Notion API is annoyingly verbose and doesn't provide a way to disable debug logging.
 * So, we'll just use our own
 */
public class DummyLogger implements NotionLogger {

    private static final Logger LOG = LoggerFactory.getLogger(VNotionManager.class); // Use the Notion logger, as this belongs to the Notion part

    @Override
    public void debug(@NotNull String s) {

    }

    @Override
    public boolean isDebugEnabled() {
        return false;
    }

    @Override
    public void debug(@NotNull String s, @Nullable Throwable throwable) {

    }

    @Override
    public void info(@NotNull String s, @Nullable Throwable throwable) {
        // Those just spam the raw HTTP responses, so we'll just ignore them
    }

    @Override
    public void warn(@NotNull String s, @Nullable Throwable throwable) {
        LOG.warn(s, throwable);
    }

    @Override
    public void error(@NotNull String s, @Nullable Throwable throwable) {
        LOG.error(s, throwable);
    }

    @Override
    public void info(@NotNull String s) {
        // Those just spam the raw HTTP responses, so we'll just ignore them
    }

    @Override
    public void warn(@NotNull String s) {
        LOG.warn(s);
    }

    @Override
    public void error(@NotNull String s) {
        LOG.error(s);
    }
}
