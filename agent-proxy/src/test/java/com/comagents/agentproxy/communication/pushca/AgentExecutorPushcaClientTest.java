package com.comagents.agentproxy.communication.pushca;

import com.comagents.agentproxy.agent_setup.FakePushcaConnector;
import com.comagents.agentproxy.conf.AgentProxyConf;
import com.comagents.agentproxy.conf.DeviceIdExtractor;
import com.comagents.commons.communication.ComagentsMessage;
import com.comagents.commons.domain.AiAgent;
import com.comagents.commons.domain.AiHarnessProvider;
import com.comagents.commons.domain.agent_context.AgentContext;
import com.comagents.commons.domain.agent_context.StringAgentContextItem;
import com.comagents.commons.request.StartAgentSessionRequest;
import com.comagents.commons.response.AgentSessionResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentExecutorPushcaClientTest {

    private static final AgentContext RESPONSE = new AgentContext(List.of(new StringAgentContextItem("response", "done")));

    private final FakePushcaConnector connector = new FakePushcaConnector();
    private final AgentExecutorPushcaClient client = new AgentExecutorPushcaClient(connector,
            new AgentProxyConf("0.1", "https://github.com/acme/comagents.git", "token", "key", "https://pushca.example",
                    "pushca-key", AiHarnessProvider.CLAUDE_CODE, new DeviceIdExtractor("executor-device-1", "host")));

    @Test
    void connectsOnceAtStartupAndKeepsTheConnection() {
        client.open();

        assertEquals(1, connector.connections.size(), "opened at startup");
        client.deliverToOrchestrator("orchestrator-device-1", RESPONSE);
        client.deliverToOrchestrator("orchestrator-device-1", RESPONSE);

        assertEquals(1, connector.connections.size(), "never opened again while it works");
        assertEquals(2, connector.connections.getFirst().broadcasts.size());
    }

    @Test
    void deliveryConnectsWhenTheStartupConnectFailed() {
        connector.connectFailure = new IllegalStateException("Pushca unreachable");
        client.open();
        assertTrue(connector.connections.isEmpty());

        connector.connectFailure = null;
        client.deliverToOrchestrator("orchestrator-device-1", RESPONSE);

        assertEquals(1, connector.connections.size());
        assertEquals(1, connector.connections.getFirst().broadcasts.size());
    }

    @Test
    void reconnectsOnceWhenTheKeptConnectionBroke() {
        client.deliverToOrchestrator("orchestrator-device-1", RESPONSE);
        FakePushcaConnector.FakeConnection broken = connector.connections.getFirst();
        broken.broadcastFailure = new IllegalStateException("Web socket connection is broken");

        client.deliverToOrchestrator("orchestrator-device-1", RESPONSE);

        assertTrue(broken.closed);
        assertEquals(2, connector.connections.size());
        FakePushcaConnector.FakeConnection reconnected = connector.connections.get(1);
        assertEquals(1, reconnected.broadcasts.size());
        ComagentsMessage message = ComagentsMessage.fromComagentsMessageString(reconnected.broadcasts.getFirst().message());
        assertEquals(RESPONSE, message.comagentsMessagePayload().payload());
    }

    @Test
    void failsWhenReconnectingFailsTooAndConnectsAgainOnTheNextDelivery() {
        client.deliverToOrchestrator("orchestrator-device-1", RESPONSE);
        FakePushcaConnector.FakeConnection broken = connector.connections.getFirst();
        broken.broadcastFailure = new IllegalStateException("Web socket connection is broken");
        IllegalStateException unreachable = new IllegalStateException("Pushca unreachable");
        connector.connectFailure = unreachable;

        assertSame(unreachable, assertThrows(IllegalStateException.class,
                () -> client.deliverToOrchestrator("orchestrator-device-1", RESPONSE)));
        assertTrue(broken.closed);

        connector.connectFailure = null;
        client.deliverToOrchestrator("orchestrator-device-1", RESPONSE);

        assertEquals(2, connector.connections.size());
        assertEquals(1, connector.connections.get(1).broadcasts.size());
    }

    @Test
    void theMessageIdIsTheResponsesSequenceId() {
        AgentSessionResponse response = new AgentSessionResponse(new StartAgentSessionRequest(UUID.randomUUID(),
                AiAgent.CODE_REVIEW, null, "sequence-1", "orchestrator-device-1", "MBugai"), RESPONSE, 1L,
                "executor-device-1");

        client.deliverToOrchestrator("orchestrator-device-1", response);

        assertEquals("sequence-1", sentMessage(0).id());
    }

    @Test
    void theMessageIdIsNewForAResponseWithoutSequenceId() {
        client.deliverToOrchestrator("orchestrator-device-1", RESPONSE);
        client.deliverToOrchestrator("orchestrator-device-1", RESPONSE);

        String first = sentMessage(0).id();
        assertEquals(first, UUID.fromString(first).toString());
        assertNotEquals(first, sentMessage(1).id());
    }

    @Test
    void doesNotRetryWhenTheFirstConnectFails() {
        connector.connectFailure = new IllegalStateException("Pushca unreachable");

        assertThrows(IllegalStateException.class, () -> client.deliverToOrchestrator("orchestrator-device-1", RESPONSE));

        assertTrue(connector.connections.isEmpty());
    }

    private ComagentsMessage sentMessage(int index) {
        return ComagentsMessage.fromComagentsMessageString(connector.connections.getFirst().broadcasts.get(index).message());
    }
}
