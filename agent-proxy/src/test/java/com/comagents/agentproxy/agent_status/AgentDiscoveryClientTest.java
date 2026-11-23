package com.comagents.agentproxy.agent_status;

import bmv.org.pushca.client.exception.PushcaCommandException;
import com.comagents.agentproxy.communication.pushca.FakePushcaConnectionFactory;
import com.comagents.commons.communication.ComagentsMessage;
import com.comagents.commons.domain.AiHarnessProvider;
import com.comagents.commons.response.AgentInfoResponse;
import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.comagents.commons.response.AgentInfoResponse.AGENT_DISCOVERY_SEQUENCE_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest
@Property(name = "agent-proxy.device-id", value = "executor-device-1")
@Property(name = "claude.session.max-concurrent", value = "3")
class AgentDiscoveryClientTest {

    @Inject
    AgentDiscoveryClient discoveryClient;

    // the context's Pushca: records what was sent
    @Inject
    FakePushcaConnectionFactory connectionFactory;

    // the executor's connection, opened on startup, stays; what earlier tests sent is forgotten
    @BeforeEach
    void resetPushca() {
        connectionFactory.reset();
    }

    @Test
    void sendsAgentInfoToAllOrchestrators() {
        discoveryClient.sendAgentInfo();

        List<FakePushcaConnectionFactory.Broadcast> broadcasts = connectionFactory.connections.getFirst().broadcasts;
        assertEquals(1, broadcasts.size());
        assertEquals("comagents-main", broadcasts.getFirst().receivers().workSpaceId);
        assertEquals("agent-orchestrator@comagents.com", broadcasts.getFirst().receivers().accountId);
        assertNull(broadcasts.getFirst().receivers().deviceId, "any orchestrator device");
        assertEquals("AGENT_ORCHESTRATOR", broadcasts.getFirst().receivers().applicationId);
        // a ComagentsMessage, read as the orchestrator reads it
        ComagentsMessage message = ComagentsMessage.fromComagentsMessageString(broadcasts.getFirst().message());
        assertEquals(AgentInfoResponse.class.getName(), message.payloadClassName());
        AgentInfoResponse info = (AgentInfoResponse) message.comagentsMessagePayload().payload();
        assertEquals(AiHarnessProvider.CLAUDE_CODE, info.harnessProvider());
        assertEquals("executor-device-1", info.deviceId());
        assertEquals(3, info.maxSlots());
        assertEquals(3, info.freeSlots());
        assertEquals(AGENT_DISCOVERY_SEQUENCE_ID, info.sequenceId());
        assertEquals(message.id(), info.sequenceId());
    }

    @Test
    void failedSendIsOnlyLogged() {
        FakePushcaConnectionFactory.FakeConnection executor = connectionFactory.connections.getFirst();
        // refused, as by Pushca: not retried
        executor.broadcastFailure = new PushcaCommandException("Send message", "no such receiver");

        discoveryClient.sendAgentInfo();

        assertTrue(executor.broadcasts.isEmpty());
    }
}
