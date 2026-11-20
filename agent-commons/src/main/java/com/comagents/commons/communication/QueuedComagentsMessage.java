package com.comagents.commons.communication;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

/**
 * A comagents message waiting in the {@link ComagentsMessageQueue} for its {@code address}, to be sent before
 * {@code expiresAt}; {@code sent} completes once it is sent, or exceptionally when it can't be.
 */
public record QueuedComagentsMessage(DeliveryAddress address, ComagentsMessage message, Instant expiresAt,
                                     CompletableFuture<Void> sent) {

    public QueuedComagentsMessage(DeliveryAddress address, ComagentsMessage message, Instant expiresAt) {
        this(address, message, expiresAt, new CompletableFuture<>());
    }

    /** Whether it has expired by {@code time}: nobody waits for it then, so it isn't sent any more. */
    public boolean isExpired(Instant time) {
        return !time.isBefore(expiresAt);
    }
}
