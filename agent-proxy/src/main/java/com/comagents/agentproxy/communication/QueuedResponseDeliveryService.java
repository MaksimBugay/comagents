package com.comagents.agentproxy.communication;

import com.comagents.commons.communication.ComagentsMessage;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Delivers through the {@link ComagentsMessageQueue}: each response or error, as a {@link ComagentsMessage}, waits
 * there for its turn, and the delivery returns once it is sent, or after {@code agent-proxy.response-timeout}. A
 * message not sent by then is dropped: the orchestrator has timed out meanwhile and asks again.
 */
@Singleton
public class QueuedResponseDeliveryService implements ResponseDeliveryService {

    private static final Logger LOG = LoggerFactory.getLogger(QueuedResponseDeliveryService.class);

    private final ComagentsMessageQueue queue;
    private final Duration responseTimeout;

    public QueuedResponseDeliveryService(ComagentsMessageQueue queue,
                                         @Value("${agent-proxy.response-timeout:30s}") Duration responseTimeout) {
        this.queue = queue;
        this.responseTimeout = responseTimeout;
    }

    /**
     * Sends {@code response}, wrapped into a {@link ComagentsMessage} with the id {@code sequenceId}, to the
     * orchestrator device {@code orchestratorDeviceId}; blocks until it is sent, or the response timeout has passed.
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
     * {@code sequenceId}, to the orchestrator device {@code orchestratorDeviceId}; blocks until it is sent, or the
     * response timeout has passed.
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
        QueuedComagentsMessage queued = new QueuedComagentsMessage(orchestratorDeviceId, message,
                Instant.now().plus(responseTimeout));
        queue.put(queued);
        try {
            queued.sent().get(responseTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            logDropped(orchestratorDeviceId, message);
        } catch (ExecutionException e) {
            // expired before its turn, or before the connection came back
            if (queued.isExpired(Instant.now())) {
                logDropped(orchestratorDeviceId, message);
                return;
            }
            // the consumer's failure, as if this thread had sent it
            if (e.getCause() instanceof RuntimeException failure) {
                throw failure;
            }
            throw new IllegalStateException(e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while delivering " + message.id(), e);
        }
    }

    private void logDropped(String orchestratorDeviceId, ComagentsMessage message) {
        LOG.error("Message {} ({}) for orchestrator device {} not sent within {}, dropped", message.id(),
                message.payloadClassName(), orchestratorDeviceId, responseTimeout);
    }
}
