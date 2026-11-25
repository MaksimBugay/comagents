package com.comagents.agentproxy.agent_status;

import com.comagents.agentproxy.agent_setup.claude.ClaudeAgentSessionRegistry;
import com.comagents.agentproxy.conf.AgentProxyConf;
import com.comagents.commons.domain.AiHarnessProvider;
import com.comagents.commons.response.AgentInfoResponse;
import jakarta.inject.Singleton;

import java.util.UUID;

@Singleton
public class AgentExecutorInfoService {

    private final AiHarnessProvider harnessProvider;
    private final String version;
    private final String deviceId;
    private final ClaudeAgentSessionRegistry sessionRegistry;

    public AgentExecutorInfoService(AgentProxyConf agentProxyConf, ClaudeAgentSessionRegistry sessionRegistry) {
        this.harnessProvider = agentProxyConf.getAiHarnessProvider();
        this.version = agentProxyConf.getVersion();
        this.deviceId = agentProxyConf.getDeviceId();
        this.sessionRegistry = sessionRegistry;
    }

    /** This executor: its harness, version, device and free session slots. */
    public AgentInfoResponse getInfo() {
        // answers no start request: a sequence id of its own
        return getInfo(UUID.randomUUID().toString());
    }

    /** This executor, in answer to the request {@code sequenceId}. */
    public AgentInfoResponse getInfo(String sequenceId) {
        return new AgentInfoResponse(harnessProvider, version, deviceId, sessionRegistry.maxSlots(), sessionRegistry.freeSlots(),
                sessionRegistry.archivedSessions(), sessionRegistry.finishedSessions(), sequenceId);
    }
}
