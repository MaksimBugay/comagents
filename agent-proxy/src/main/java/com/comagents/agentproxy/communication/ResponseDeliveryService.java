package com.comagents.agentproxy.communication;

import com.comagents.commons.communication.WithSequenceId;

/** Hands what the executor has for an orchestrator to that orchestrator's device. */
public interface ResponseDeliveryService {

    /**
     * Sends {@code response} to the orchestrator device {@code orchestratorDeviceId}. A {@link WithSequenceId}
     * response goes with its sequence id, so the orchestrator matches it to its request; any other gets a new id.
     */
    <T> void deliverToOrchestrator(String orchestratorDeviceId, T response);
}
