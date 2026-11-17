package com.comagents.agentproxy.agent_setup;

import com.comagents.commons.domain.AiAgent;

/** The agent is known, but no setup service exists for its harness provider yet. */
public class NoAgentSetupServiceException extends RuntimeException {

    public NoAgentSetupServiceException(AiAgent agent) {
        super("No setup service for harness provider " + agent.getAiHarnessProvider() + " (agent " + agent.getAgentName() + ")");
    }
}
