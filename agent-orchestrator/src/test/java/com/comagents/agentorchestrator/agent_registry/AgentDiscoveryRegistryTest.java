package com.comagents.agentorchestrator.agent_registry;

import com.comagents.commons.domain.AiHarnessProvider;
import com.comagents.commons.response.AgentInfoResponse;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentDiscoveryRegistryTest {

    private final AgentDiscoveryRegistry registry = new AgentDiscoveryRegistry(Duration.ofMinutes(1));

    @Test
    void keepsTheFresherInfoOfAnExecutor() {
        registry.update(info("executor-1", 3, 3, 0, 2_000));

        registry.update(info("executor-1", 3, 1, 0, 1_000));
        assertEquals(3, registry.getBestAvailable(AiHarnessProvider.CLAUDE_CODE, false).orElseThrow().freeSlots(), "an older info is ignored");

        registry.update(info("executor-1", 3, 2, 0, 3_000));
        assertEquals(2, registry.getBestAvailable(AiHarnessProvider.CLAUDE_CODE, false).orElseThrow().freeSlots(), "a fresher one replaces it");
        assertEquals(1, registry.getAllAvailable(false).size());
    }

    @Test
    void aRemovedExecutorIsBackWithItsNextReport() {
        registry.update(info("executor-1", 3, 3, 0, 1_000));
        registry.update(info("executor-2", 3, 1, 0, 1_000));

        registry.remove("executor-1");
        assertEquals(List.of("executor-2"), registry.getAll().stream().map(AgentInfoResponse::deviceId).toList());

        registry.update(info("executor-1", 3, 2, 0, 2_000));
        assertEquals("executor-1", registry.getBestAvailable(AiHarnessProvider.CLAUDE_CODE, false).orElseThrow().deviceId());
    }

    @Test
    void theBestAvailableRunsTheGivenHarness() {
        registry.update(new AgentInfoResponse(AiHarnessProvider.CODEX, "0.1", "codex-1", 3, 3, 0, "sequence-1", 1_000));
        registry.update(info("claude-1", 3, 1, 0, 1_000));

        assertEquals("claude-1",
                registry.getBestAvailable(AiHarnessProvider.CLAUDE_CODE, false).orElseThrow().deviceId());
        assertEquals("codex-1", registry.getBestAvailable(AiHarnessProvider.CODEX, false).orElseThrow().deviceId());
    }

    @Test
    void ordersByFreeSlotsThenArchivedSessions() {
        registry.update(info("one-free", 3, 1, 0, 1_000));
        registry.update(info("two-free-two-archived", 3, 2, 2, 1_000));
        registry.update(info("two-free-one-archived", 3, 2, 1, 1_000));
        registry.update(info("full", 3, 0, 0, 1_000));

        assertEquals(List.of("two-free-one-archived", "two-free-two-archived", "one-free"),
                registry.getAllAvailable(false).stream().map(AgentInfoResponse::deviceId).toList());
        assertEquals("two-free-one-archived", registry.getBestAvailable(AiHarnessProvider.CLAUDE_CODE, false).orElseThrow().deviceId());
    }

    @Test
    void oneSessionPerExecutorTakesOnlyIdleExecutors() {
        registry.update(info("idle", 1, 1, 0, 1_000));
        registry.update(info("idle-with-more-slots", 3, 3, 0, 1_000));
        registry.update(info("busy", 3, 2, 0, 1_000));
        registry.update(info("archived", 1, 1, 1, 1_000));

        assertEquals(List.of("idle-with-more-slots", "idle"),
                registry.getAllAvailable(true).stream().map(AgentInfoResponse::deviceId).toList());
    }

    @Test
    void allIncludesExecutorsWithoutFreeSlots() {
        registry.update(info("full", 3, 0, 0, 1_000));
        registry.update(info("one-free", 3, 1, 0, 1_000));

        assertEquals(List.of("one-free", "full"), registry.getAll().stream().map(AgentInfoResponse::deviceId).toList());
    }

    @Test
    void nothingAvailable() {
        registry.update(info("full", 1, 0, 0, 1_000));

        assertEquals(Optional.empty(), registry.getBestAvailable(AiHarnessProvider.CLAUDE_CODE, false));
        assertEquals(Optional.empty(), registry.getBestAvailable(AiHarnessProvider.CLAUDE_CODE, true));
    }

    @Test
    void forgetsExecutorsThatStopReporting() {
        AgentDiscoveryRegistry shortLived = new AgentDiscoveryRegistry(Duration.ofMillis(200));
        shortLived.update(info("executor-1", 3, 3, 0, 1_000));
        assertEquals(1, shortLived.getAllAvailable(false).size());

        await().atMost(Duration.ofSeconds(5)).until(() -> shortLived.getAllAvailable(false).isEmpty());
    }

    @Test
    void rejectsInfoWithoutDeviceId() {
        assertThrows(IllegalArgumentException.class, () -> registry.update(info(" ", 3, 3, 0, 1_000)));
    }

    private static AgentInfoResponse info(String deviceId, int maxSlots, int freeSlots, int archivedSessions, long createdAt) {
        return new AgentInfoResponse(AiHarnessProvider.CLAUDE_CODE, "0.1", deviceId, maxSlots, freeSlots, archivedSessions,
                "sequence-1", createdAt);
    }
}
