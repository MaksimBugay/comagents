package com.comagents.agentproxy.communication;

import bmv.org.pushca.client.exception.WebsocketConnectionIsBrokenException;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeoutException;

/** Sends the {@link ComagentsMessageQueue}'s messages, one by one in queue order. */
public interface ComagentsQueueConsumer {

    // the Pushca client's message for a send while its socket isn't open
    String BROKEN_CONNECTION_MESSAGE = "Web socket connection is broken";
    Duration RETRY_INTERVAL = Duration.ofSeconds(1);

    /**
     * Sends {@code message} once; blocks until it is sent.
     *
     * @throws RuntimeException when it can't be sent
     */
    void consume(QueuedComagentsMessage message);

    /**
     * {@link #consume}s {@code message}; while the connection is broken, tries again every second until the message
     * expires (the Pushca client reconnects by itself meanwhile). Any other failure ends it at once.
     *
     * @throws RuntimeException when it can't be sent: the last failure, a broken connection's once it expired
     */
    default void consumeWithRetry(QueuedComagentsMessage message) {
        while (true) {
            try {
                consume(message);
                return;
            } catch (RuntimeException e) {
                if (!isBrokenConnection(e)) {
                    throw e;
                }
                pause();
                // nobody waits for it any more
                if (message.isExpired(Instant.now())) {
                    throw e;
                }
            }
        }
    }

    /**
     * Takes the messages of {@code queue} one by one, consumes each and completes its {@code sent} with the outcome;
     * a message that expired while it waited is dropped unsent. Returns once the thread is interrupted.
     */
    default void consumeAll(ComagentsMessageQueue queue) {
        while (true) {
            QueuedComagentsMessage message;
            try {
                message = queue.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (message.isExpired(Instant.now())) {
                message.sent().completeExceptionally(new TimeoutException("Expired at " + message.expiresAt()));
                continue;
            }
            try {
                consumeWithRetry(message);
                message.sent().complete(null);
            } catch (RuntimeException e) {
                message.sent().completeExceptionally(e);
            }
        }
    }

    // what the Pushca client throws for a send on a broken connection: not open (a plain IllegalStateException, unlike
    // PushcaCommandException for a command Pushca rejects), or not acknowledged by Pushca
    private static boolean isBrokenConnection(RuntimeException e) {
        return e instanceof WebsocketConnectionIsBrokenException
                || e.getClass() == IllegalStateException.class && BROKEN_CONNECTION_MESSAGE.equals(e.getMessage());
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
