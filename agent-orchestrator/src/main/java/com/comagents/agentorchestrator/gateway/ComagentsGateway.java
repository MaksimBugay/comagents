package com.comagents.agentorchestrator.gateway;

import com.comagents.commons.communication.ComagentsMessageDeliveryService;
import com.comagents.commons.communication.DeliveryAddress;
import com.comagents.commons.communication.WithSequenceId;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Request/response over the one-way comagents messages: a request is sent to an agent executor and its caller waits,
 * in the waiting hall, for the response with the request's sequence id, which a listener hands over
 * ({@link #completeWithResponse}, {@link #completeWithError}). A caller waits at most
 * {@code agent-orchestrator.response-timeout} (30 s by default) from when its request was registered, sending
 * included.
 */
@Singleton
public class ComagentsGateway {

    private static final Logger LOG = LoggerFactory.getLogger(ComagentsGateway.class);

    private final ComagentsMessageDeliveryService comagentsMessageDeliveryService;
    private final Duration responseTimeout;
    // sequence id -> the waiting caller's future; an entry outlives its caller only when removing it failed
    private final Cache<String, CompletableFuture<Object>> waitingHall;

    public ComagentsGateway(ComagentsMessageDeliveryService comagentsMessageDeliveryService,
                            @Value("${agent-orchestrator.response-timeout:30s}") Duration responseTimeout) {
        this.comagentsMessageDeliveryService = comagentsMessageDeliveryService;
        this.responseTimeout = responseTimeout;
        this.waitingHall = Caffeine.newBuilder()
                .expireAfterWrite(responseTimeout)
                .build();
    }

    /**
     * Sends {@code request} to {@code address} and waits for its response. The message id is the request's own
     * sequence id when it has a non-blank one ({@link WithSequenceId}: the executor rejects a request whose sequence
     * id isn't the message id), otherwise a new one.
     *
     * @return the response, a {@code responseClass}
     * @throws IllegalArgumentException when another request with the same sequence id is still waiting
     * @throws IllegalStateException    when no response came within the response timeout (caused by a
     *                                  {@link TimeoutException}), or the wait was interrupted
     * @throws ClassCastException       when the response isn't a {@code responseClass}
     * @throws RuntimeException         the error received in place of the response, or the one sending failed with
     */
    public <P, R> R registerRequest(DeliveryAddress address, P request, Class<R> responseClass) {
        Validate.notNull(request, "request must not be null");
        long deadline = System.nanoTime() + responseTimeout.toNanos();
        String sequenceId = sequenceIdOf(request);
        CompletableFuture<Object> response = new CompletableFuture<>();
        Validate.isTrue(waitingHall.asMap().putIfAbsent(sequenceId, response) == null,
                "Request %s is already waiting for its response", sequenceId);
        try {
            comagentsMessageDeliveryService.deliverToAddress(address, sequenceId, request);
            return responseClass.cast(await(sequenceId, response, deadline - System.nanoTime()));
        } finally {
            waitingHall.asMap().remove(sequenceId, response);
        }
    }

    /** Hands {@code response} to the caller waiting for request {@code sequenceId}, if any is still waiting. */
    public <R> void completeWithResponse(String sequenceId, R response) {
        take(sequenceId).ifPresent(waiting -> waiting.complete(response));
    }

    /** Fails the caller waiting for request {@code sequenceId} with {@code error}, if any is still waiting. */
    public void completeWithError(String sequenceId, Throwable error) {
        Validate.notNull(error, "error must not be null");
        take(sequenceId).ifPresent(waiting -> waiting.completeExceptionally(error));
    }

    private static String sequenceIdOf(Object request) {
        if (request instanceof WithSequenceId withSequenceId && StringUtils.isNotBlank(withSequenceId.sequenceId())) {
            return withSequenceId.sequenceId();
        }
        return UUID.randomUUID().toString();
    }

    private Optional<CompletableFuture<Object>> take(String sequenceId) {
        CompletableFuture<Object> waiting = (sequenceId == null) ? null : waitingHall.asMap().remove(sequenceId);
        if (waiting == null) {
            // its caller timed out meanwhile, or it was never asked for here
            LOG.warn("No request {} waiting for its response", sequenceId);
        }
        return Optional.ofNullable(waiting);
    }

    private Object await(String sequenceId, CompletableFuture<Object> response, long remainingNanos) {
        try {
            return response.get(remainingNanos, TimeUnit.NANOSECONDS);
        } catch (TimeoutException e) {
            throw new IllegalStateException(
                    "No response to request %s within %s".formatted(sequenceId, responseTimeout), e);
        } catch (ExecutionException e) {
            throw rethrown(e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for the response to " + sequenceId, e);
        }
    }

    private static RuntimeException rethrown(Throwable error) {
        if (error instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        if (error instanceof Error fatal) {
            throw fatal;
        }
        return new IllegalStateException(error);
    }
}
