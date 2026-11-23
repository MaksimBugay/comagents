package com.comagents.agentorchestrator.controller;

import com.comagents.agentorchestrator.agent_registry.AgentDiscoveryRegistry;
import com.comagents.agentorchestrator.conf.AgentOrchestratorConf;
import com.comagents.agentorchestrator.gateway.ComagentsGateway;
import com.comagents.commons.communication.DeliveryAddress;
import com.comagents.commons.communication.DeviceIdExtractor;
import com.comagents.commons.domain.AgentSessionLoggingLevel;
import com.comagents.commons.domain.AiAgent;
import com.comagents.commons.domain.AiHarnessProvider;
import com.comagents.commons.exception.ClaudeSessionLimitException;
import com.comagents.commons.request.StartAgentSessionRequest;
import com.comagents.commons.response.AgentInfoResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.List;

import static com.comagents.commons.response.AgentInfoResponse.AGENT_DISCOVERY_SEQUENCE_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class StartAgentSessionHelperTest {

    private final ComagentsGateway gateway = mock(ComagentsGateway.class);
    private final AgentDiscoveryRegistry registry = new AgentDiscoveryRegistry(Duration.ofMinutes(1));
    private final StartAgentSessionHelper helper = new StartAgentSessionHelper(gateway, registry,
            new AgentOrchestratorConf("0.1", "https://pushca.example", "pushca-key",
                    new DeviceIdExtractor("orchestrator-own", "host")));

    @Test
    void startsOnTheExecutorWithMostFreeSlotsOfTheAgentsHarness() {
        registry.update(discovered("claude-1", AiHarnessProvider.CLAUDE_CODE, 1));
        registry.update(discovered("claude-2", AiHarnessProvider.CLAUDE_CODE, 2));
        registry.update(discovered("codex-1", AiHarnessProvider.CODEX, 3));
        AgentInfoResponse started = new AgentInfoResponse(AiHarnessProvider.CLAUDE_CODE, "0.1", "claude-2", 3, 1, 0,
                "sequence-1");
        when(gateway.registerRequest(any(), any(), eq(AgentInfoResponse.class))).thenReturn(started);

        assertSame(started, helper.startAgentSession(AiAgent.CODE_REVIEW, null, List.of("CODE_REVIEW"), "Maks",
                "waiter-device-1", AgentSessionLoggingLevel.DEBUG));

        ArgumentCaptor<StartAgentSessionRequest> request = ArgumentCaptor.forClass(StartAgentSessionRequest.class);
        verify(gateway).registerRequest(eq(DeliveryAddress.agentExecutor("claude-2", AiHarnessProvider.CLAUDE_CODE)),
                request.capture(), eq(AgentInfoResponse.class));
        StartAgentSessionRequest sent = request.getValue();
        assertNotNull(sent.sessionId());
        assertNotNull(sent.sequenceId());
        assertEquals(AiAgent.CODE_REVIEW, sent.agent());
        assertEquals("orchestrator-own", sent.originatorDeviceId(), "this orchestrator's own device");
        assertEquals("waiter-device-1", sent.agentResponseWaiterDeviceId());
        assertEquals(List.of("CODE_REVIEW"), sent.agentCallSequence());
        assertEquals("Maks", sent.actorAccountId());
        assertEquals(AgentSessionLoggingLevel.DEBUG, sent.loggingLevel());
    }

    @Test
    void theExecutorsReplyUpdatesTheRegistry() {
        AgentInfoResponse discovered = discovered("claude-1", AiHarnessProvider.CLAUDE_CODE, 1);
        registry.update(discovered);
        // made after the discovery report, as the executor's reply always is
        when(gateway.registerRequest(any(), any(), eq(AgentInfoResponse.class))).thenReturn(
                new AgentInfoResponse(AiHarnessProvider.CLAUDE_CODE, "0.1", "claude-1", 3, 0, 0, "sequence-1",
                        discovered.createdAt() + 1));

        helper.startAgentSession(AiAgent.CODE_REVIEW, null, null, "Maks", null, null);

        assertEquals(0, registry.getAll().getFirst().freeSlots());
        assertEquals(List.of(), registry.getAllAvailable(false));
    }

    @Test
    void anExecutorWithoutSlotsIsRemovedAndTheLimitRethrown() {
        registry.update(discovered("claude-1", AiHarnessProvider.CLAUDE_CODE, 2));
        registry.update(discovered("claude-2", AiHarnessProvider.CLAUDE_CODE, 1));
        ClaudeSessionLimitException full = new ClaudeSessionLimitException(3);
        when(gateway.registerRequest(eq(executor("claude-1")), any(), eq(AgentInfoResponse.class))).thenThrow(full);

        assertSame(full, assertThrows(ClaudeSessionLimitException.class, () -> helper.startAgentSession(
                AiAgent.CODE_REVIEW, null, null, "Maks", null, null)));

        assertEquals(List.of("claude-2"), registry.getAll().stream().map(AgentInfoResponse::deviceId).toList(),
                "claude-1 is gone until it reports again");
        verify(gateway, times(1)).registerRequest(any(), any(), eq(AgentInfoResponse.class));
    }

    @Test
    void theCallersRetryGoesToAnotherExecutor() {
        registry.update(discovered("claude-1", AiHarnessProvider.CLAUDE_CODE, 2));
        registry.update(discovered("claude-2", AiHarnessProvider.CLAUDE_CODE, 1));
        when(gateway.registerRequest(eq(executor("claude-1")), any(), eq(AgentInfoResponse.class)))
                .thenThrow(new ClaudeSessionLimitException(3));
        when(gateway.registerRequest(eq(executor("claude-2")), any(), eq(AgentInfoResponse.class))).thenReturn(
                new AgentInfoResponse(AiHarnessProvider.CLAUDE_CODE, "0.1", "claude-2", 3, 0, 0, "sequence-2"));
        assertThrows(ClaudeSessionLimitException.class, () -> helper.startAgentSession(AiAgent.CODE_REVIEW, null,
                null, "Maks", null, null));

        assertEquals("claude-2", helper.startAgentSession(AiAgent.CODE_REVIEW, null, null, "Maks",
                null, null).deviceId());
    }

    @Test
    void theLoggingLevelIsInfoWhenNotGiven() {
        registry.update(discovered("claude-1", AiHarnessProvider.CLAUDE_CODE, 1));
        when(gateway.registerRequest(any(), any(), eq(AgentInfoResponse.class))).thenReturn(
                new AgentInfoResponse(AiHarnessProvider.CLAUDE_CODE, "0.1", "claude-1", 3, 0, 0, "sequence-1"));

        helper.startAgentSession(AiAgent.CODE_REVIEW, null, null, "Maks", null, null);

        ArgumentCaptor<StartAgentSessionRequest> request = ArgumentCaptor.forClass(StartAgentSessionRequest.class);
        verify(gateway).registerRequest(any(), request.capture(), eq(AgentInfoResponse.class));
        assertEquals(AgentSessionLoggingLevel.INFO, request.getValue().loggingLevel());
        assertEquals(List.of(), request.getValue().agentCallSequence());
    }

    @Test
    void failsWithoutAFreeExecutorOfTheAgentsHarness() {
        registry.update(discovered("codex-1", AiHarnessProvider.CODEX, 3));
        registry.update(discovered("claude-1", AiHarnessProvider.CLAUDE_CODE, 0));

        assertThrows(IllegalStateException.class, () -> helper.startAgentSession(AiAgent.CODE_REVIEW, null,
                null, "Maks", null, null));
        verifyNoInteractions(gateway);
    }

    @Test
    void passesOnWhyTheExecutorDidNotStartTheSession() {
        registry.update(discovered("claude-1", AiHarnessProvider.CLAUDE_CODE, 1));
        IllegalArgumentException refused = new IllegalArgumentException("Duplicate item STRING focus");
        when(gateway.registerRequest(any(), any(), eq(AgentInfoResponse.class))).thenThrow(refused);

        assertSame(refused, assertThrows(IllegalArgumentException.class, () -> helper.startAgentSession(
                AiAgent.CODE_REVIEW, null, null, "Maks", null, null)));
    }

    private static DeliveryAddress executor(String deviceId) {
        return DeliveryAddress.agentExecutor(deviceId, AiHarnessProvider.CLAUDE_CODE);
    }

    private static AgentInfoResponse discovered(String deviceId, AiHarnessProvider harness, int freeSlots) {
        return new AgentInfoResponse(harness, "0.1", deviceId, 3, freeSlots, 0, AGENT_DISCOVERY_SEQUENCE_ID);
    }
}
