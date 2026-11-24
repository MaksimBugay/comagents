package com.comagents.agentorchestrator.controller;

import com.comagents.agentorchestrator.conf.AgentOrchestratorConf;
import com.comagents.agentorchestrator.exception.ComagentsResponseTimeoutException;
import com.comagents.agentorchestrator.gateway.ComagentsGateway;
import com.comagents.commons.communication.DeliveryAddress;
import com.comagents.commons.domain.AgentSessionAttributes;
import com.comagents.commons.exception.AgentResponseNotReadyException;
import com.comagents.commons.exception.AgentSessionNotFoundException;
import com.comagents.commons.request.AgentResponseRequest;
import com.comagents.commons.response.AgentResponseReply;
import com.comagents.commons.response.AgentSessionResponse;
import jakarta.inject.Singleton;

import java.util.UUID;

/**
 * A session's response: the one its executor sent ({@link AgentSessionResponseRegistry}), else asked from the
 * executor that runs it ({@link RunningAgentSessionRegistry}).
 */
@Singleton
public class AgentSessionResponseHelper {

    private final AgentSessionResponseRegistry agentSessionResponseRegistry;
    private final RunningAgentSessionRegistry runningAgentSessionRegistry;
    private final ComagentsGateway gateway;
    private final AgentOrchestratorConf conf;

    public AgentSessionResponseHelper(AgentSessionResponseRegistry agentSessionResponseRegistry,
                                      RunningAgentSessionRegistry runningAgentSessionRegistry,
                                      ComagentsGateway gateway,
                                      AgentOrchestratorConf conf) {
        this.agentSessionResponseRegistry = agentSessionResponseRegistry;
        this.runningAgentSessionRegistry = runningAgentSessionRegistry;
        this.gateway = gateway;
        this.conf = conf;
    }

    /**
     * @throws AgentSessionNotFoundException     when neither this orchestrator nor the session's executor knows it
     * @throws AgentResponseNotReadyException    when the session has no response yet
     * @throws ComagentsResponseTimeoutException when the executor's reply didn't come in time
     */
    public AgentSessionResponse getResponse(UUID sessionId) {
        return agentSessionResponseRegistry.get(sessionId)
                .orElseGet(() -> askExecutor(runningSession(sessionId)));
    }

    private AgentSessionAttributes runningSession(UUID sessionId) {
        return runningAgentSessionRegistry.get(sessionId)
                .orElseThrow(() -> new AgentSessionNotFoundException("No session " + sessionId));
    }

    private AgentSessionResponse askExecutor(AgentSessionAttributes session) {
        AgentResponseRequest request = new AgentResponseRequest(session.agent(), session.sessionId(),
                UUID.randomUUID().toString(), conf.getDeviceId());
        return gateway.exchange(
                DeliveryAddress.agentExecutor(session.agentExecutorDeviceId(), session.agent().getAiHarnessProvider()),
                request,
                AgentResponseReply.class
        ).response();
    }
}
