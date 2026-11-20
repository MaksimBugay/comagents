package com.comagents.agentorchestrator.communication;

import com.comagents.agentorchestrator.agent_registry.AgentDiscoveryRegistry;
import com.comagents.commons.communication.ComagentsMessageListener;
import com.comagents.commons.response.AgentInfo;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

/**
 * The {@link AgentInfo}s of the agent executors, their discovery reports and their replies to start requests alike,
 * into the {@link AgentDiscoveryRegistry}. An error in place of an info (a failed start) is not this listener's.
 */
@Singleton
public class AgentInfoListener implements ComagentsMessageListener<AgentInfo> {

    private static final Logger LOG = LoggerFactory.getLogger(AgentInfoListener.class);

    private final AgentDiscoveryRegistry agentDiscoveryRegistry;

    public AgentInfoListener(AgentDiscoveryRegistry agentDiscoveryRegistry) {
        this.agentDiscoveryRegistry = agentDiscoveryRegistry;
    }

    @Override
    public Class<AgentInfo> getPayloadClass() {
        return AgentInfo.class;
    }

    @Override
    public void consumePayload(String sequenceId, AgentInfo agentInfo) {
        agentDiscoveryRegistry.update(agentInfo);
        LOG.info("Agent executors after AgentInfo {} from {}: {}", sequenceId, agentInfo.deviceId(),
                agentDiscoveryRegistry.getAll().stream().map(AgentInfoListener::describe).toList());
    }

    private static String describe(AgentInfo info) {
        return "%s (%s %s): %d/%d slots free, %d archived, reported %s".formatted(info.deviceId(),
                info.harnessProvider(), info.version(), info.freeSlots(), info.maxSlots(), info.archivedSessions(),
                Instant.ofEpochMilli(info.createdAt()));
    }
}
