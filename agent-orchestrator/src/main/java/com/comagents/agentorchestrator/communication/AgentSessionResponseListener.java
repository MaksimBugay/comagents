package com.comagents.agentorchestrator.communication;

import com.comagents.agentorchestrator.controller.AgentSessionResponseRegistry;
import com.comagents.agentorchestrator.controller.RunningAgentSessionRegistry;
import com.comagents.commons.communication.ComagentsMessageListener;
import com.comagents.commons.response.AgentSessionResponse;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The responses of finished agent sessions, as the executors send them: a session with its response is no longer
 * running ({@link RunningAgentSessionRegistry}), and its response is kept ({@link AgentSessionResponseRegistry}).
 */
@Singleton
public class AgentSessionResponseListener implements ComagentsMessageListener<AgentSessionResponse> {

    private static final Logger LOG = LoggerFactory.getLogger(AgentSessionResponseListener.class);

    private final RunningAgentSessionRegistry runningAgentSessionRegistry;
    private final AgentSessionResponseRegistry agentSessionResponseRegistry;

    public AgentSessionResponseListener(RunningAgentSessionRegistry runningAgentSessionRegistry,
                                        AgentSessionResponseRegistry agentSessionResponseRegistry) {
        this.runningAgentSessionRegistry = runningAgentSessionRegistry;
        this.agentSessionResponseRegistry = agentSessionResponseRegistry;
    }

    @Override
    public Class<AgentSessionResponse> getPayloadClass() {
        return AgentSessionResponse.class;
    }

    @Override
    public void consumePayload(String sequenceId, AgentSessionResponse response) {
        runningAgentSessionRegistry.remove(response.request().sessionId());
        agentSessionResponseRegistry.append(response);
        LOG.info("Response of session {} received from executor {}", response.request().sessionId(),
                response.executorDeviceId());
    }
}
