package com.comagents.agentproxy.communication;

import com.comagents.commons.communication.ComagentsMessage;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

/**
 * A comagents message waiting in the {@link ComagentsMessageQueue} for the orchestrator device
 * {@code orchestratorDeviceId}, to be sent before {@code expiresAt}; {@code sent} completes once it is sent, or
 * exceptionally when it can't be.
 */
public record QueuedComagentsMessage(String orchestratorDeviceId, ComagentsMessage message, Instant expiresAt,
                                     CompletableFuture<Void> sent) {

    public QueuedComagentsMessage(String orchestratorDeviceId, ComagentsMessage message, Instant expiresAt) {
        this(orchestratorDeviceId, message, expiresAt, new CompletableFuture<>());
    }

    /** Whether it has expired by {@code time}: nobody waits for it then, so it isn't sent any more. */
    public boolean isExpired(Instant time) {
        return !time.isBefore(expiresAt);
    }
}
