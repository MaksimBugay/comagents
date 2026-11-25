package com.comagents.agentproxy.communication;

import com.comagents.agentproxy.agent_call.AgentCallResponses;
import com.comagents.commons.communication.ComagentsError;
import com.comagents.commons.communication.ComagentsMessageListener;
import com.comagents.commons.response.AgentSessionResponse;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The responses of the agent sessions that sessions here called through their agent tools: the called session's
 * executor sends its response to this executor's device too (the start request's {@code agentResponseWaiterDeviceId}),
 * and it goes to the waiting call or resumes the caller ({@link AgentCallResponses}).
 */
@Singleton
public class AgentSessionResponseListener implements ComagentsMessageListener<AgentSessionResponse> {

    private static final Logger LOG = LoggerFactory.getLogger(AgentSessionResponseListener.class);

    private final AgentCallResponses agentCallResponses;

    public AgentSessionResponseListener(AgentCallResponses agentCallResponses) {
        this.agentCallResponses = agentCallResponses;
    }

    @Override
    public Class<AgentSessionResponse> getPayloadClass() {
        return AgentSessionResponse.class;
    }

    // resuming a caller starts a process: off the Pushca client's receiving thread
    @Override
    public void consumePayload(String sequenceId, AgentSessionResponse response) {
        Thread.ofVirtual().name("agent-call-response-" + response.request().sessionId()).start(() -> {
            try {
                agentCallResponses.onResponse(response);
            } catch (RuntimeException e) {
                LOG.error("Handling the response of session {} failed", response.request().sessionId(), e);
            }
        });
    }

    @Override
    public void consumeError(String sequenceId, ComagentsError error) {
        LOG.warn("An agent session response {} came as an error: {}", sequenceId, error.errorMessage());
    }
}
