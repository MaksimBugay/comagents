package com.comagents.agentproxy.communication;

import com.comagents.commons.communication.ComagentsMessageListener;
import com.comagents.commons.request.StartAgentSessionRequest;
import jakarta.inject.Singleton;

@Singleton
public class StartAgentSessionListener implements ComagentsMessageListener<StartAgentSessionRequest> {
    @Override
    public Class<StartAgentSessionRequest> getPayloadClass() {
        return StartAgentSessionRequest.class;
    }

    @Override
    public void consumePayload(StartAgentSessionRequest payload) {

    }
}
