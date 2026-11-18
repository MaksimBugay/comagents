package com.comagents.agentproxy.communication;

import com.comagents.agentproxy.agent_status.AgentStatusService;
import com.comagents.commons.communication.ComagentsMessageListener;
import com.comagents.commons.request.AgentStatusRequest;
import com.comagents.commons.response.AgentStatusResponse;
import jakarta.inject.Singleton;

/** Status requests from an orchestrator over Pushca, as {@code GET /agent-status/{agentName}/{sessionId}}. */
@Singleton
public class AgentStatusListener implements ComagentsMessageListener<AgentStatusRequest> {

    private final AgentStatusService agentStatusService;
    private final ResponseDeliveryService responseDeliveryService;

    public AgentStatusListener(AgentStatusService agentStatusService,
                               ResponseDeliveryService responseDeliveryService) {
        this.agentStatusService = agentStatusService;
        this.responseDeliveryService = responseDeliveryService;
    }

    @Override
    public Class<AgentStatusRequest> getPayloadClass() {
        return AgentStatusRequest.class;
    }

    /**
     * @throws com.comagents.agentproxy.agent_status.AgentSessionNotFoundException when the session is unknown
     */
    // the message's sequence id is the request's: ComagentsMessageDispatcher checked it
    @Override
    public void consumePayload(String sequenceId, AgentStatusRequest request) {
        responseDeliveryService.deliverToOrchestrator(request.orchestratorDeviceId(),
                new AgentStatusResponse(request, agentStatusService.getStatus(request.agent(), request.sessionId())));
    }
}
