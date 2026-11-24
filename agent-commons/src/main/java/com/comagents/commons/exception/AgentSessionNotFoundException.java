package com.comagents.commons.exception;

import com.comagents.commons.domain.AiAgent;

import java.util.UUID;

/** The session is unknown: never started, or already cleaned up. */
public class AgentSessionNotFoundException extends RuntimeException {

    public AgentSessionNotFoundException(AiAgent agent, UUID sessionId) {
        super("No session " + sessionId + " for agent " + agent.getAgentName());
    }

    /** With the message as received: how an orchestrator recreates it from an executor's error. */
    public AgentSessionNotFoundException(String message) {
        super(message);
    }
}
