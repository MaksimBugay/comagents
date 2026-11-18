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
 * Delivers through the {@link ComagentsMessageQueue}: each payload or error, as a {@link ComagentsMessage}, waits
 * there for its turn, and the delivery returns once it is sent, or after {@code agent-proxy.message-timeout}. A
 * message not sent by then is dropped: for a response, the orchestrator has timed out meanwhile and asks again.
 */
@Singleton
public class QueuedComagentsMessageDeliveryService implements ComagentsMessageDeliveryService {

    private static final Logger LOG = LoggerFactory.getLogger(QueuedComagentsMessageDeliveryService.class);

    private final ComagentsMessageQueue queue;
    private final Duration messageTimeout;

    public QueuedComagentsMessageDeliveryService(
            ComagentsMessageQueue queue, @Value("${agent-proxy.message-timeout:30s}") Duration messageTimeout) {
        this.queue = queue;
        this.messageTimeout = messageTimeout;
    }

    /**
     * Sends {@code payload}, wrapped into a {@link ComagentsMessage} with the id {@code sequenceId}, to the device
     * {@code deviceId}; blocks until it is sent, or the message timeout has passed.
     *
     * @throws NullPointerException     when the device id, sequence id or payload is null
     * @throws IllegalArgumentException when the device id or sequence id is blank
     * @throws IllegalStateException    when it can't be sent
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T> void deliverToDevice(String deviceId, String sequenceId, T payload) {
        Validate.notNull(payload, "payload must not be null");
        // the payload's runtime class: the receiver reads the message by that class name
        deliver(deviceId, ComagentsMessage.ofPayload(sequenceId, payload, (Class<T>) payload.getClass()));
    }

    /**
     * Sends {@code error}, wrapped into a {@link ComagentsMessage} of {@code payloadClass} with the id
     * {@code sequenceId}, to the device {@code deviceId}; blocks until it is sent, or the message timeout has passed.
     *
     * @throws NullPointerException     when the device id, sequence id, payload class or error is null
     * @throws IllegalArgumentException when the device id or sequence id is blank
     * @throws IllegalStateException    when it can't be sent
     */
    @Override
    public void deliverErrorToDevice(String deviceId, String sequenceId, Class<?> payloadClass, Throwable error) {
        Validate.notNull(error, "error must not be null");
        deliver(deviceId, ComagentsMessage.ofError(sequenceId, payloadClass, error));
    }

    private void deliver(String deviceId, ComagentsMessage message) {
        Validate.notBlank(deviceId, "deviceId must not be blank");
        QueuedComagentsMessage queued = new QueuedComagentsMessage(deviceId, message,
                Instant.now().plus(messageTimeout));
        queue.put(queued);
        try {
            queued.sent().get(messageTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            logDropped(deviceId, message);
        } catch (ExecutionException e) {
            // expired before its turn, or before the connection came back
            if (queued.isExpired(Instant.now())) {
                logDropped(deviceId, message);
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

    private void logDropped(String deviceId, ComagentsMessage message) {
        LOG.error("Message {} ({}) for device {} not sent within {}, dropped", message.id(),
                message.payloadClassName(), deviceId, messageTimeout);
    }
}
