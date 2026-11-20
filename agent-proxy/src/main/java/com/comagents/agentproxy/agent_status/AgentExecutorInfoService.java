package com.comagents.agentproxy.agent_status;

import com.comagents.agentproxy.agent_setup.claude.ClaudeAgentSessionRegistry;
import com.comagents.agentproxy.conf.AgentProxyConf;
import com.comagents.commons.response.AgentInfo;
import jakarta.inject.Singleton;

import java.util.UUID;

@Singleton
public class AgentExecutorInfoService {

    private final String version;
    private final String deviceId;
    private final ClaudeAgentSessionRegistry sessionRegistry;

    public AgentExecutorInfoService(AgentProxyConf agentProxyConf, ClaudeAgentSessionRegistry sessionRegistry) {
        this.version = agentProxyConf.getVersion();
        this.deviceId = agentProxyConf.getDeviceId();
        this.sessionRegistry = sessionRegistry;
    }

    /** What this executor runs {@code agentName} with. */
    public AgentInfo getInfo(String agentName) {
        // answers no start request: a sequence id of its own
        return getInfo(agentName, UUID.randomUUID().toString());
    }

    /** What this executor runs {@code agentName} with, in answer to the request {@code sequenceId}. */
    public AgentInfo getInfo(String agentName, String sequenceId) {
        return new AgentInfo(agentName, version, deviceId, sessionRegistry.freeSlots(),
                sessionRegistry.archivedSessions(), sequenceId);
    }
}
