package com.comagents.commons.communication;

/** A request from an orchestrator: its response goes back to the orchestrator device that sent it. */
public interface WithOrchestratorDeviceId {

    /** The orchestrator device that sent the request and gets the response. */
    String orchestratorDeviceId();
}
