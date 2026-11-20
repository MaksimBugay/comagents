package com.comagents.commons.communication;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Runs a command again while the connection is broken: every second until a deadline, the transport's client
 * reconnecting by itself meanwhile. Which failures mean a broken connection is up to the transport; any other failure
 * ends it at once.
 */
public final class BrokenConnectionRetry {

    private static final Duration RETRY_INTERVAL = Duration.ofSeconds(1);

    private BrokenConnectionRetry() {
    }

    /**
     * @param isBrokenConnection whether a failure of {@code command} means a broken connection
     * @return what {@code command} returns, once it succeeds
     * @throws RuntimeException the last failure: any but a broken connection's at once, that one once
     *                          {@code deadline} has passed
     */
    public static <T> T callWithRetry(Supplier<T> command, Instant deadline,
                                      Predicate<RuntimeException> isBrokenConnection) {
        while (true) {
            try {
                return command.get();
            } catch (RuntimeException e) {
                if (!isBrokenConnection.test(e)) {
                    throw e;
                }
                pause();
                // nobody waits for it any more
                if (!Instant.now().isBefore(deadline)) {
                    throw e;
                }
            }
        }
    }

    /** {@link #callWithRetry} for a command without a result. */
    public static void runWithRetry(Runnable command, Instant deadline,
                                    Predicate<RuntimeException> isBrokenConnection) {
        callWithRetry(() -> {
            command.run();
            return null;
        }, deadline, isBrokenConnection);
    }

    private static void pause() {
        try {
            Thread.sleep(RETRY_INTERVAL);
        } catch (InterruptedException e) {
            // closed meanwhile
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for the connection", e);
        }
    }
}
