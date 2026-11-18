package com.comagents.agentproxy.communication;

import com.comagents.commons.communication.ComagentsMessageListener;
import com.comagents.commons.communication.WithOrchestratorDeviceId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A request from an orchestrator over Pushca that the orchestrator device gets an answer to, with the request's
 * sequence id: the {@code R} response, or the error that took its place.
 *
 * @param <T> the request
 * @param <R> the response
 */
public abstract class ComagentsBaseListener<T extends WithOrchestratorDeviceId, R>
        implements ComagentsMessageListener<T> {

    private static final Logger LOG = LoggerFactory.getLogger(ComagentsBaseListener.class);

    private final ResponseDeliveryService responseDeliveryService;

    protected ComagentsBaseListener(ResponseDeliveryService responseDeliveryService) {
        this.responseDeliveryService = responseDeliveryService;
    }

    // the message's sequence id is the request's: ComagentsMessageDispatcher checked it
    @Override
    public void consumePayload(String sequenceId, T request) {
        R response;
        try {
            response = prepareResponse(request);
        } catch (RuntimeException e) {
            LOG.warn("{} {} of orchestrator device {} failed: {}", getPayloadClass().getSimpleName(), sequenceId,
                    request.orchestratorDeviceId(), e.getMessage());
            responseDeliveryService.deliverErrorToOrchestrator(request.orchestratorDeviceId(), sequenceId,
                    getResponseClass(), e);
            return;
        }
        responseDeliveryService.deliverToOrchestrator(request.orchestratorDeviceId(), sequenceId, response);
    }

    /** The class of the response, which an error replaces in the message. */
    protected abstract Class<R> getResponseClass();

    /** The answer to {@code request}; a failure goes to the orchestrator device as an error in its place. */
    protected abstract R prepareResponse(T request);
}
