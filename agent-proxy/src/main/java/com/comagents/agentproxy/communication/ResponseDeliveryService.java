package com.comagents.agentproxy.communication;

import com.comagents.commons.communication.WithSequenceId;

/**
 * Hands what the executor has for an orchestrator to that orchestrator's device: a response, or why there is none.
 */
public interface ResponseDeliveryService {

    /**
     * Sends {@code response} to the orchestrator device {@code orchestratorDeviceId} with the request's
     * {@code sequenceId}, so the orchestrator matches it to its request.
     */
    <T> void deliverToOrchestrator(String orchestratorDeviceId, String sequenceId, T response);

    /** Sends {@code response} with its own sequence id, which is its request's. */
    default <T extends WithSequenceId> void deliverToOrchestrator(String orchestratorDeviceId, T response) {
        deliverToOrchestrator(orchestratorDeviceId, response.sequenceId(), response);
    }

    /**
     * Sends {@code error} to the orchestrator device {@code orchestratorDeviceId} in place of the
     * {@code responseClass} response: with the request's {@code sequenceId}, so the orchestrator matches it to its
     * request, and the response class, so it reaches the orchestrator's listener for that response.
     */
    void deliverErrorToOrchestrator(String orchestratorDeviceId, String sequenceId, Class<?> responseClass,
                                    Throwable error);
}
