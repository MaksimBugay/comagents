package com.comagents.agentorchestrator.communication;

import com.comagents.agentorchestrator.gateway.ComagentsGateway;
import com.comagents.commons.response.AgentResponseReply;
import jakarta.inject.Singleton;

@Singleton
public class AgentResponseReplyListener extends ComagentsGatewayResponseListener<AgentResponseReply> {
    protected AgentResponseReplyListener(ComagentsGateway gateway) {
        super(gateway);
    }

    @Override
    public Class<AgentResponseReply> getPayloadClass() {
        return AgentResponseReply.class;
    }
}
