package com.comagents.agentproxy.communication;

import java.time.Instant;
import java.util.concurrent.TimeoutException;

/** Sends the {@link ComagentsMessageQueue}'s messages, one by one in queue order. */
public interface ComagentsQueueConsumer {

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
        BrokenConnectionRetry.runWithRetry(() -> consume(message), message.expiresAt());
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
}
