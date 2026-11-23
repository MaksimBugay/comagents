package com.comagents.agentproxy.communication;

import com.comagents.agentproxy.agent_setup.AgentSessionStarter;
import com.comagents.commons.communication.ComagentsBaseListener;
import com.comagents.commons.communication.ComagentsMessageDeliveryService;
import com.comagents.commons.request.StartAgentSessionRequest;
import com.comagents.commons.response.AgentInfoResponse;
import jakarta.inject.Singleton;

/**
 * Start requests from an orchestrator over Pushca; the orchestrator device gets back who runs the session, or why it
 * wasn't started.
 */
@Singleton
public class StartAgentSessionListener extends ComagentsBaseListener<StartAgentSessionRequest, AgentInfoResponse> {

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
    protected Class<AgentInfoResponse> getResponseClass() {
        return AgentInfoResponse.class;
    }

    @Override
    protected AgentInfoResponse prepareResponse(StartAgentSessionRequest request) {
        return agentSessionStarter.start(request);
    }
}
