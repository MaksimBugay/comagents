package com.comagents.agentorchestrator.communication;

import com.comagents.commons.communication.ComagentsMessageDeliveryService;
import com.comagents.commons.communication.ComagentsMessageQueue;
import com.comagents.commons.communication.QueuedComagentsMessageDeliveryService;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;

import java.time.Duration;

/**
 * The orchestrator's one {@link ComagentsMessageQueue}, which its Pushca client sends from, and the
 * {@link ComagentsMessageDeliveryService} that queues the messages there, each waiting at most
 * {@code agent-orchestrator.message-timeout} to be sent.
 */
@Factory
public class ComagentsMessageDeliveryFactory {

    @Singleton
    public ComagentsMessageQueue comagentsMessageQueue() {
        return new ComagentsMessageQueue();
    }

    @Singleton
    public ComagentsMessageDeliveryService comagentsMessageDeliveryService(
            ComagentsMessageQueue queue,
            @Value("${agent-orchestrator.message-timeout:30s}") Duration messageTimeout) {
        return new QueuedComagentsMessageDeliveryService(queue, messageTimeout);
    }
}
