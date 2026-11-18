package com.comagents.agentproxy.communication;

import com.comagents.agentproxy.agent_setup.AgentSessionStarter;
import com.comagents.commons.request.StartAgentSessionRequest;
import com.comagents.commons.response.AgentInfo;
import jakarta.inject.Singleton;

/**
 * Start requests from an orchestrator over Pushca; the orchestrator device gets back who runs the session, or why it
 * wasn't started.
 */
@Singleton
public class StartAgentSessionListener extends ComagentsBaseListener<StartAgentSessionRequest, AgentInfo> {

    private final AgentSessionStarter agentSessionStarter;

    public StartAgentSessionListener(AgentSessionStarter agentSessionStarter,
                                     ComagentsMessageDeliveryService messageDeliveryService) {
        super(messageDeliveryService);
        this.agentSessionStarter = agentSessionStarter;
    }

    @Override
    public Class<StartAgentSessionRequest> getPayloadClass() {
        return StartAgentSessionRequest.class;
    }

    @Override
    protected Class<AgentInfo> getResponseClass() {
        return AgentInfo.class;
    }

    @Override
    protected AgentInfo prepareResponse(StartAgentSessionRequest request) {
        return agentSessionStarter.start(request);
    }
}
