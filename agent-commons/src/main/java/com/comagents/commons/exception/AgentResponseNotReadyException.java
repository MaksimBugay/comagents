package com.comagents.commons.exception;

import com.comagents.commons.domain.AgentSessionState;
import com.comagents.commons.domain.AiAgent;
import org.apache.commons.lang3.EnumUtils;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * The session is known but has no response yet: it is in progress, or archived for a later resume. Every session
 * leaves a response when it ends, so one will come.
 */
public class AgentResponseNotReadyException extends RuntimeException {

    private static final String STATE_PREFIX = "session is ";

    private final AgentSessionState state;

    public AgentResponseNotReadyException(AiAgent agent, UUID sessionId, AgentSessionState state) {
        super("No response from agent " + agent.getAgentName() + " for session " + sessionId + " yet: "
                + STATE_PREFIX + state);
        this.state = state;
    }

    /**
     * With the message as received: how an orchestrator recreates it from an executor's error, the state read back
     * from the message's ending.
     */
    public AgentResponseNotReadyException(String message) {
        super(message);
        this.state = stateOf(message);
    }

    /** Null when recreated from a message without one. */
    public @Nullable AgentSessionState getState() {
        return state;
    }

    private static @Nullable AgentSessionState stateOf(String message) {
        return EnumUtils.getEnum(AgentSessionState.class, StringUtils.substringAfterLast(message, STATE_PREFIX));
    }
}
