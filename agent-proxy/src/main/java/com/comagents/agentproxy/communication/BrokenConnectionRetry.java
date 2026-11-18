package com.comagents.agentproxy.communication;

import bmv.org.pushca.client.exception.WebsocketConnectionIsBrokenException;
import bmv.org.pushca.client.exception.WebsocketConnectionIsNotOpenException;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

/**
 * Runs a Pushca command again while the connection is broken: every second until a deadline, the Pushca client
 * reconnecting by itself meanwhile. Any other failure ends it at once.
 */
public final class BrokenConnectionRetry {

    private static final Duration RETRY_INTERVAL = Duration.ofSeconds(1);

    private BrokenConnectionRetry() {
    }

    /**
     * @return what {@code command} returns, once it succeeds
     * @throws RuntimeException the last failure: any but a broken connection's at once, that one once
     *                          {@code deadline} has passed
     */
    public static <T> T callWithRetry(Supplier<T> command, Instant deadline) {
        while (true) {
            try {
                return command.get();
            } catch (RuntimeException e) {
                if (!isBrokenConnection(e)) {
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
    public static void runWithRetry(Runnable command, Instant deadline) {
        callWithRetry(() -> {
            command.run();
            return null;
        }, deadline);
    }

    // what the Pushca client throws for a command on a broken connection: not open, or not acknowledged by Pushca
    private static boolean isBrokenConnection(RuntimeException e) {
        return e instanceof WebsocketConnectionIsNotOpenException || e instanceof WebsocketConnectionIsBrokenException;
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
