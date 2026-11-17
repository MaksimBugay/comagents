package com.comagents.agentproxy.communication.pushca;

import com.comagents.agentproxy.agent_setup.FakePushcaConnector;
import com.comagents.agentproxy.conf.AgentProxyConf;
import com.comagents.agentproxy.conf.DeviceIdExtractor;
import com.comagents.commons.communication.ComagentsMessage;
import com.comagents.commons.domain.agent_context.AgentContext;
import com.comagents.commons.domain.agent_context.StringAgentContextItem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentExecutorPushcaClientTest {

    private static final AgentContext RESPONSE = new AgentContext(List.of(new StringAgentContextItem("response", "done")));

    private final FakePushcaConnector connector = new FakePushcaConnector();
    private final AgentExecutorPushcaClient client = new AgentExecutorPushcaClient(connector,
            new AgentProxyConf("https://github.com/acme/comagents.git", "token", "key", "https://pushca.example",
                    "pushca-key", new DeviceIdExtractor("executor-device-1", "host")));

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
        assertEquals(RESPONSE, ComagentsMessage.fromMessageString(reconnected.broadcasts.getFirst().message(),
                AgentContext.class).payload());
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
    void doesNotRetryWhenTheFirstConnectFails() {
        connector.connectFailure = new IllegalStateException("Pushca unreachable");

        assertThrows(IllegalStateException.class, () -> client.deliverToOrchestrator("orchestrator-device-1", RESPONSE));

        assertTrue(connector.connections.isEmpty());
    }
}
