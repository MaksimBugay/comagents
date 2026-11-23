package com.comagents.agentorchestrator.communication;

import com.comagents.agentorchestrator.gateway.ComagentsGateway;
import com.comagents.commons.response.AgentStatusResponse;
import jakarta.inject.Singleton;

@Singleton
public class AgentStatusResponseListener extends ComagentsGatewayResponseListener<AgentStatusResponse> {
    protected AgentStatusResponseListener(ComagentsGateway gateway) {
        super(gateway);
    }

    @Override
    public Class<AgentStatusResponse> getPayloadClass() {
        return AgentStatusResponse.class;
    }
}
