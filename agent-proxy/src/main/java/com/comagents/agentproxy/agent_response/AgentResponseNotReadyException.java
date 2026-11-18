package com.comagents.agentproxy.agent_response;

import com.comagents.commons.domain.AgentSessionState;
import com.comagents.commons.domain.AiAgent;

import java.util.UUID;

/**
 * The session is known but has no response yet: it is in progress, or archived for a later resume. Every session
 * leaves a response when it ends, so one will come.
 */
public class AgentResponseNotReadyException extends RuntimeException {

    private final AgentSessionState state;

    public AgentResponseNotReadyException(AiAgent agent, UUID sessionId, AgentSessionState state) {
        super("No response from agent " + agent.getAgentName() + " for session " + sessionId + " yet: session is "
                + state);
        this.state = state;
    }

    public AgentSessionState getState() {
        return state;
    }
}
