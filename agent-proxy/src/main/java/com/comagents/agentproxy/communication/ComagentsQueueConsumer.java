package com.comagents.agentproxy.communication;

/** Sends the {@link ComagentsMessageQueue}'s messages, one by one in queue order. */
public interface ComagentsQueueConsumer {

    /**
     * Sends {@code message}; blocks until it is sent.
     *
     * @throws RuntimeException when it can't be sent
     */
    void consume(QueuedComagentsMessage message);

    /**
     * Takes the messages of {@code queue} one by one, consumes each and completes its {@code sent} with the outcome;
     * returns once the thread is interrupted.
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
            try {
                consume(message);
                message.sent().complete(null);
            } catch (RuntimeException e) {
                message.sent().completeExceptionally(e);
            }
        }
    }
}
