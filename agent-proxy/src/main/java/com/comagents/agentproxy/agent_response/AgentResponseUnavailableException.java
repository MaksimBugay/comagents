package com.comagents.agentproxy.agent_response;

import com.comagents.commons.response.AgentResponseUnavailable;

/** The agent has no response for the session; {@link #getUnavailable()} tells whether one may still come. */
public class AgentResponseUnavailableException extends RuntimeException {

    private final transient AgentResponseUnavailable unavailable;

    public AgentResponseUnavailableException(AgentResponseUnavailable unavailable) {
        super(unavailable.message());
        this.unavailable = unavailable;
    }

    public AgentResponseUnavailable getUnavailable() {
        return unavailable;
    }
}
