package com.comagents.agentproxy.communication;

import com.comagents.agentproxy.agent_status.AgentStatusService;
import com.comagents.commons.request.AgentStatusRequest;
import com.comagents.commons.response.AgentStatusResponse;
import jakarta.inject.Singleton;

/**
 * Status requests from an orchestrator over Pushca, as {@code GET /agent-status/{agentName}/{sessionId}}; the
 * orchestrator device gets back the status, or why there is none (an unknown session).
 */
@Singleton
public class AgentStatusListener extends ComagentsBaseListener<AgentStatusRequest, AgentStatusResponse> {

    private final AgentStatusService agentStatusService;

    public AgentStatusListener(AgentStatusService agentStatusService,
                               ResponseDeliveryService responseDeliveryService) {
        super(responseDeliveryService);
        this.agentStatusService = agentStatusService;
    }

    @Override
    public Class<AgentStatusRequest> getPayloadClass() {
        return AgentStatusRequest.class;
    }

    @Override
    protected Class<AgentStatusResponse> getResponseClass() {
        return AgentStatusResponse.class;
    }

    @Override
    protected AgentStatusResponse prepareResponse(AgentStatusRequest request) {
        return new AgentStatusResponse(request, agentStatusService.getStatus(request.agent(), request.sessionId()));
    }
}
