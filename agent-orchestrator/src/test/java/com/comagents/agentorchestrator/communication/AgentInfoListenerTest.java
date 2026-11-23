package com.comagents.agentorchestrator.communication;

import com.comagents.agentorchestrator.agent_registry.AgentDiscoveryRegistry;
import com.comagents.agentorchestrator.communication.pushca.FakePushcaConnectionFactory;
import com.comagents.commons.communication.ComagentsMessage;
import com.comagents.commons.domain.AiHarnessProvider;
import com.comagents.commons.response.AgentInfoResponse;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

@MicronautTest
class AgentInfoListenerTest {

    @Inject
    AgentDiscoveryRegistry registry;

    // the context's Pushca: the orchestrator's connection, opened on startup
    @Inject
    FakePushcaConnectionFactory connectionFactory;

    @Test
    void anExecutorsReportReachesTheRegistry() {
        AgentInfoResponse info = new AgentInfoResponse(AiHarnessProvider.CLAUDE_CODE, "0.1", "executor-device-1", 3, 2, 0,
                "sequence-1");

        // as an executor's AgentDiscoveryClient sends it
        connectionFactory.connections.getFirst().receive(
                ComagentsMessage.ofPayload("sequence-1", info, AgentInfoResponse.class).toString());

        await("handled on a thread of its own").atMost(Duration.ofSeconds(5))
                .until(() -> registry.getBestAvailable(false).isPresent());
        AgentInfoResponse registered = registry.getBestAvailable(false).orElseThrow();
        assertEquals("executor-device-1", registered.deviceId());
        assertEquals(2, registered.freeSlots());
    }
}
