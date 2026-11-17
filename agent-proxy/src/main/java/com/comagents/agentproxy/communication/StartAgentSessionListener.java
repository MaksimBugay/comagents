package com.comagents.agentproxy.communication;

import com.comagents.agentproxy.agent_setup.AgentSessionStarter;
import com.comagents.commons.communication.ComagentsMessageListener;
import com.comagents.commons.request.StartAgentSessionRequest;
import jakarta.inject.Singleton;

/** Start requests from an orchestrator over Pushca; the orchestrator device gets back who runs the session. */
@Singleton
public class StartAgentSessionListener implements ComagentsMessageListener<StartAgentSessionRequest> {

    private final AgentSessionStarter agentSessionStarter;
    private final ResponseDeliveryService responseDeliveryService;

    public StartAgentSessionListener(AgentSessionStarter agentSessionStarter,
                                     ResponseDeliveryService responseDeliveryService) {
        this.agentSessionStarter = agentSessionStarter;
        this.responseDeliveryService = responseDeliveryService;
    }

    @Override
    public Class<StartAgentSessionRequest> getPayloadClass() {
        return StartAgentSessionRequest.class;
    }

    // the message's sequence id is the request's: ComagentsMessageDispatcher checked it
    @Override
    public void consumePayload(String sequenceId, StartAgentSessionRequest request) {
        responseDeliveryService.deliverToOrchestrator(request.sequenceId(), request.orchestratorDeviceId(),
                agentSessionStarter.start(request));
    }
}
