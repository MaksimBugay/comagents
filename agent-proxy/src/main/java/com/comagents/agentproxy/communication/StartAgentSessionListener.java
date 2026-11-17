package com.comagents.agentproxy.communication;

import com.comagents.agentproxy.conf.AgentProxyConf;
import com.comagents.commons.communication.ComagentsMessageListener;
import com.comagents.commons.request.StartAgentSessionRequest;
import jakarta.inject.Singleton;

@Singleton
public class StartAgentSessionListener implements ComagentsMessageListener<StartAgentSessionRequest> {

    private final String version;
    private final String deviceId;

    public StartAgentSessionListener(AgentProxyConf agentProxyConf) {
        this.version = agentProxyConf.getVersion();
        this.deviceId = agentProxyConf.getDeviceId();
    }

    @Override
    public Class<StartAgentSessionRequest> getPayloadClass() {
        return StartAgentSessionRequest.class;
    }

    @Override
    public void consumePayload(StartAgentSessionRequest request) {

    }
}
