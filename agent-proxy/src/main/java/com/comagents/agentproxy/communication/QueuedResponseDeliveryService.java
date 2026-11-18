package com.comagents.agentproxy.communication;

import com.comagents.commons.communication.ComagentsMessage;
import jakarta.inject.Singleton;
import org.apache.commons.lang3.Validate;

import java.util.concurrent.CompletionException;

/**
 * Delivers through the {@link ComagentsMessageQueue}: each response or error, as a {@link ComagentsMessage}, waits
 * there for its turn, and the delivery returns once it is sent.
 */
@Singleton
public class QueuedResponseDeliveryService implements ResponseDeliveryService {

    private final ComagentsMessageQueue queue;

    public QueuedResponseDeliveryService(ComagentsMessageQueue queue) {
        this.queue = queue;
    }

    /**
     * Sends {@code response}, wrapped into a {@link ComagentsMessage} with the id {@code sequenceId}, to the
     * orchestrator device {@code orchestratorDeviceId}; blocks until it is sent.
     *
     * @throws NullPointerException     when the device id, sequence id or response is null
     * @throws IllegalArgumentException when the device id or sequence id is blank
     * @throws IllegalStateException    when it can't be sent
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T> void deliverToOrchestrator(String orchestratorDeviceId, String sequenceId, T response) {
        Validate.notNull(response, "response must not be null");
        // the payload's runtime class: the orchestrator reads the message by that class name
        deliver(orchestratorDeviceId, ComagentsMessage.ofPayload(sequenceId, response, (Class<T>) response.getClass()));
    }

    /**
     * Sends {@code error}, wrapped into a {@link ComagentsMessage} of {@code responseClass} with the id
     * {@code sequenceId}, to the orchestrator device {@code orchestratorDeviceId}; blocks until it is sent.
     *
     * @throws NullPointerException     when the device id, sequence id, response class or error is null
     * @throws IllegalArgumentException when the device id or sequence id is blank
     * @throws IllegalStateException    when it can't be sent
     */
    @Override
    public void deliverErrorToOrchestrator(String orchestratorDeviceId, String sequenceId, Class<?> responseClass,
                                           Throwable error) {
        Validate.notNull(error, "error must not be null");
        deliver(orchestratorDeviceId, ComagentsMessage.ofError(sequenceId, responseClass, error));
    }

    private void deliver(String orchestratorDeviceId, ComagentsMessage message) {
        Validate.notBlank(orchestratorDeviceId, "orchestratorDeviceId must not be blank");
        QueuedComagentsMessage queued = new QueuedComagentsMessage(orchestratorDeviceId, message);
        queue.put(queued);
        try {
            queued.sent().join();
        } catch (CompletionException e) {
            // the consumer's failure, as if this thread had sent it
            if (e.getCause() instanceof RuntimeException failure) {
                throw failure;
            }
            throw e;
        }
    }
}
