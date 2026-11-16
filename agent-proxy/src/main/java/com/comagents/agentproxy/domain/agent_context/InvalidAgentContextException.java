package com.comagents.agentproxy.domain.agent_context;

/** The agent context of a start request doesn't fit the agent: answered with 400. */
public class InvalidAgentContextException extends IllegalArgumentException {

    public InvalidAgentContextException(String message) {
        super(message);
    }
}
