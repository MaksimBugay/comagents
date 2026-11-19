package com.comagents.agentproxy.agent_status;

import com.comagents.agentproxy.conf.AgentProxyConf;
import com.comagents.commons.response.AgentInfo;
import jakarta.inject.Singleton;

import java.util.UUID;

@Singleton
public class AgentInfoService {

    private final String version;
    private final String deviceId;

    public AgentInfoService(AgentProxyConf agentProxyConf) {
        this.version = agentProxyConf.getVersion();
        this.deviceId = agentProxyConf.getDeviceId();
    }

    /** What this executor runs {@code agentName} with. */
    public AgentInfo getInfo(String agentName) {
        // answers no start request: a sequence id of its own
        return new AgentInfo(agentName, version, deviceId, UUID.randomUUID().toString());
    }
}
