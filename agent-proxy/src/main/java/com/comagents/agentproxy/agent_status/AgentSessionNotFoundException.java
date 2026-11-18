package com.comagents.agentproxy.agent_status;

import com.comagents.commons.domain.AiAgent;

import java.util.UUID;

/** The session is unknown: never started, or already cleaned up. */
public class AgentSessionNotFoundException extends RuntimeException {

    public AgentSessionNotFoundException(AiAgent agent, UUID sessionId) {
        super("No session " + sessionId + " for agent " + agent.getAgentName());
    }
}
