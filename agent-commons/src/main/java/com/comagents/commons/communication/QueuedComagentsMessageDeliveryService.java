package com.comagents.commons.communication;

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
 * there for its turn, and the delivery returns once it is sent, or after the message timeout. A
 * message not sent by then is dropped: for a response, the orchestrator has timed out meanwhile and asks again.
 */
public class QueuedComagentsMessageDeliveryService implements ComagentsMessageDeliveryService {

    private static final Logger LOG = LoggerFactory.getLogger(QueuedComagentsMessageDeliveryService.class);

    private final ComagentsMessageQueue queue;
    private final Duration messageTimeout;

    public QueuedComagentsMessageDeliveryService(ComagentsMessageQueue queue, Duration messageTimeout) {
        this.queue = queue;
        this.messageTimeout = messageTimeout;
    }

    /**
     * Sends {@code payload}, wrapped into a {@link ComagentsMessage} with the id {@code sequenceId}, to
     * {@code address}; blocks until it is sent, or the message timeout has passed.
     *
     * @throws NullPointerException     when the address, sequence id or payload is null
     * @throws IllegalArgumentException when the sequence id is blank
     * @throws IllegalStateException    when it can't be sent
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T> void deliverToAddress(DeliveryAddress address, String sequenceId, T payload) {
        Validate.notNull(payload, "payload must not be null");
        // the payload's runtime class: the receiver reads the message by that class name
        deliver(address, ComagentsMessage.ofPayload(sequenceId, payload, (Class<T>) payload.getClass()));
    }

    /**
     * Sends {@code error}, wrapped into a {@link ComagentsMessage} of {@code payloadClass} with the id
     * {@code sequenceId}, to {@code address}; blocks until it is sent, or the message timeout has passed.
     *
     * @throws NullPointerException     when the address, sequence id, payload class or error is null
     * @throws IllegalArgumentException when the sequence id is blank
     * @throws IllegalStateException    when it can't be sent
     */
    @Override
    public void deliverErrorToAddress(DeliveryAddress address, String sequenceId, Class<?> payloadClass,
                                     Throwable error) {
        Validate.notNull(error, "error must not be null");
        deliver(address, ComagentsMessage.ofError(sequenceId, payloadClass, error));
    }

    private void deliver(DeliveryAddress address, ComagentsMessage message) {
        Validate.notNull(address, "address must not be null");
        QueuedComagentsMessage queued = new QueuedComagentsMessage(address, message,
                Instant.now().plus(messageTimeout));
        queue.put(queued);
        try {
            queued.sent().get(messageTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            logDropped(address, message);
        } catch (ExecutionException e) {
            // expired before its turn, or before the connection came back
            if (queued.isExpired(Instant.now())) {
                logDropped(address, message);
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

    private void logDropped(DeliveryAddress address, ComagentsMessage message) {
        LOG.error("Message {} ({}) for {} not sent within {}, dropped", message.id(), message.payloadClassName(),
                address, messageTimeout);
    }
}
