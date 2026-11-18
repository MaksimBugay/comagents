package com.comagents.agentproxy.communication;

import com.comagents.commons.communication.ComagentsMessage;

import java.util.concurrent.CompletableFuture;

/**
 * A comagents message waiting in the {@link ComagentsMessageQueue} for the orchestrator device
 * {@code orchestratorDeviceId}; {@code sent} completes once it is sent, or exceptionally when it can't be.
 */
public record QueuedComagentsMessage(String orchestratorDeviceId, ComagentsMessage message,
                                     CompletableFuture<Void> sent) {

    public QueuedComagentsMessage(String orchestratorDeviceId, ComagentsMessage message) {
        this(orchestratorDeviceId, message, new CompletableFuture<>());
    }
}
