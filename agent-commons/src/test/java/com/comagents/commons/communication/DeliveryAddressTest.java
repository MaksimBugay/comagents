package com.comagents.commons.communication;

import com.comagents.commons.domain.AiHarnessProvider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DeliveryAddressTest {

    @Test
    void blankIdsAreKeptAsNull() {
        assertEquals(new DeliveryAddress(null, "device-1", null), new DeliveryAddress(" ", "device-1", ""));
    }

    @Test
    void anAddressNeedsOneId() {
        assertThrows(IllegalArgumentException.class, () -> new DeliveryAddress(null, " ", ""));
    }

    @Test
    void anAgentExecutorIsAddressedByItsHarness() {
        assertEquals(new DeliveryAddress("agent-executor@comagents.com", "executor-device-1", "CLAUDE_CODE_EXECUTOR"),
                DeliveryAddress.agentExecutor("executor-device-1", AiHarnessProvider.CLAUDE_CODE));
    }

    @Test
    void anAgentOrchestratorIsAddressedByItsApplication() {
        assertEquals(new DeliveryAddress("agent-orchestrator@comagents.com", "orchestrator-device-1",
                        "AGENT_ORCHESTRATOR"),
                DeliveryAddress.agentOrchestrator("orchestrator-device-1"));
    }

    @Test
    void agentOrchestratorsAreAllDevicesOfTheOrchestratorApplication() {
        assertEquals(new DeliveryAddress("agent-orchestrator@comagents.com", null, "AGENT_ORCHESTRATOR"),
                DeliveryAddress.agentOrchestrators());
    }

    @Test
    void agentAddressesNeedTheDevice() {
        assertThrows(IllegalArgumentException.class,
                () -> DeliveryAddress.agentExecutor(" ", AiHarnessProvider.CLAUDE_CODE));
        assertThrows(NullPointerException.class,
                () -> DeliveryAddress.agentExecutor("executor-device-1", null));
        assertThrows(NullPointerException.class, () -> DeliveryAddress.agentOrchestrator(null));
    }
}
