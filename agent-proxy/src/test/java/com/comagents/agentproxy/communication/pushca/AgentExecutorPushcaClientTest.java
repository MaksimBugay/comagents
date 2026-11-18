package com.comagents.agentproxy.communication.pushca;

import com.comagents.agentproxy.communication.ComagentsMessageReceiver;
import com.comagents.agentproxy.conf.AgentProxyConf;
import com.comagents.agentproxy.conf.DeviceIdExtractor;
import com.comagents.commons.communication.ComagentsMessage;
import com.comagents.commons.communication.ComagentsMessageListener;
import com.comagents.commons.domain.AiAgent;
import com.comagents.commons.domain.AiHarnessProvider;
import com.comagents.commons.domain.agent_context.AgentContext;
import com.comagents.commons.domain.agent_context.StringAgentContextItem;
import com.comagents.commons.request.StartAgentSessionRequest;
import com.comagents.commons.response.AgentInfo;
import com.comagents.commons.response.AgentSessionResponse;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentExecutorPushcaClientTest {

    private static final AgentContext RESPONSE = new AgentContext(List.of(new StringAgentContextItem("response", "done")));

    private final FakePushcaConnectionFactory connectionFactory = new FakePushcaConnectionFactory();
    private final RecordingListener listener = new RecordingListener();
    private final ComagentsMessageReceiver messageReceiver = new ComagentsMessageReceiver(List.of(listener));
    private final AgentExecutorPushcaClient client = new AgentExecutorPushcaClient(connectionFactory,
            () -> messageReceiver, new AgentProxyConf("0.1", "https://github.com/acme/comagents.git", "token", "key", "https://pushca.example",
                    "pushca-key", AiHarnessProvider.CLAUDE_CODE, new DeviceIdExtractor("executor-device-1", "host")));

    @Test
    void connectsOnceAtStartupAndKeepsTheConnection() {
        client.open();

        assertEquals(1, connectionFactory.connections.size(), "opened at startup");
        client.deliverToOrchestrator("orchestrator-device-1", "sequence-1", RESPONSE);
        client.deliverToOrchestrator("orchestrator-device-1", "sequence-1", RESPONSE);

        assertEquals(1, connectionFactory.connections.size(), "never opened again while it works");
        assertEquals(2, connectionFactory.connections.getFirst().broadcasts.size());
    }

    @Test
    void deliveryConnectsWhenTheStartupConnectFailed() {
        connectionFactory.connectFailure = new IllegalStateException("Pushca unreachable");
        client.open();
        assertTrue(connectionFactory.connections.isEmpty());

        connectionFactory.connectFailure = null;
        client.deliverToOrchestrator("orchestrator-device-1", "sequence-1", RESPONSE);

        assertEquals(1, connectionFactory.connections.size());
        assertEquals(1, connectionFactory.connections.getFirst().broadcasts.size());
    }

    @Test
    void reconnectsOnceWhenTheKeptConnectionBroke() {
        client.deliverToOrchestrator("orchestrator-device-1", "sequence-1", RESPONSE);
        FakePushcaConnectionFactory.FakeConnection broken = connectionFactory.connections.getFirst();
        broken.broadcastFailure = new IllegalStateException("Web socket connection is broken");

        client.deliverToOrchestrator("orchestrator-device-1", "sequence-1", RESPONSE);

        assertTrue(broken.closed);
        assertEquals(2, connectionFactory.connections.size());
        FakePushcaConnectionFactory.FakeConnection reconnected = connectionFactory.connections.get(1);
        assertEquals(1, reconnected.broadcasts.size());
        ComagentsMessage message = ComagentsMessage.fromComagentsMessageString(reconnected.broadcasts.getFirst().message());
        assertEquals(RESPONSE, message.comagentsMessagePayload().payload());
    }

    @Test
    void failsWhenReconnectingFailsTooAndConnectsAgainOnTheNextDelivery() {
        client.deliverToOrchestrator("orchestrator-device-1", "sequence-1", RESPONSE);
        FakePushcaConnectionFactory.FakeConnection broken = connectionFactory.connections.getFirst();
        broken.broadcastFailure = new IllegalStateException("Web socket connection is broken");
        IllegalStateException unreachable = new IllegalStateException("Pushca unreachable");
        connectionFactory.connectFailure = unreachable;

        assertSame(unreachable, assertThrows(IllegalStateException.class,
                () -> client.deliverToOrchestrator("orchestrator-device-1", "sequence-1", RESPONSE)));
        assertTrue(broken.closed);

        connectionFactory.connectFailure = null;
        client.deliverToOrchestrator("orchestrator-device-1", "sequence-1", RESPONSE);

        assertEquals(2, connectionFactory.connections.size());
        assertEquals(1, connectionFactory.connections.get(1).broadcasts.size());
    }

    @Test
    void theMessageIdIsTheGivenSequenceId() {
        client.deliverToOrchestrator("orchestrator-device-1", "sequence-1", RESPONSE);

        assertEquals("sequence-1", sentMessage(0).id());
    }

    @Test
    void aResponseWithSequenceIdGoesWithIt() {
        AgentSessionResponse response = new AgentSessionResponse(new StartAgentSessionRequest(UUID.randomUUID(),
                AiAgent.CODE_REVIEW, null, "sequence-1", "orchestrator-device-1", "MBugai"), RESPONSE, 1L,
                "executor-device-1");

        client.deliverToOrchestrator("orchestrator-device-1", response);

        assertEquals("sequence-1", sentMessage(0).id());
    }

    @Test
    void aBlankSequenceIdIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> client.deliverToOrchestrator("orchestrator-device-1", " ", RESPONSE));
        assertThrows(IllegalArgumentException.class, () -> client.deliverErrorToOrchestrator("orchestrator-device-1",
                " ", AgentInfo.class, new IllegalStateException("agent failed")));

        assertTrue(connectionFactory.connections.isEmpty());
    }

    @Test
    void deliversAnErrorInPlaceOfTheResponseWithTheRequestsSequenceId() {
        client.deliverErrorToOrchestrator("orchestrator-device-1", "sequence-1", AgentInfo.class,
                new IllegalStateException("agent failed"));

        ComagentsMessage message = sentMessage(0);
        assertEquals("sequence-1", message.id());
        assertEquals(AgentInfo.class.getName(), message.payloadClassName());
        assertNull(message.payloadString());
        assertEquals(IllegalStateException.class.getName(), message.error().errorClassName());
        assertEquals("agent failed", message.error().errorMessage());
        assertEquals("orchestrator-device-1", connectionFactory.connections.getFirst().broadcasts.getFirst().receivers().deviceId);
    }

    @Test
    void passesTheOrchestratorsMessagesToTheirListeners() {
        client.open();

        connectionFactory.connections.getFirst().receive(
                ComagentsMessage.ofPayload("sequence-1", RESPONSE, AgentContext.class).toString());

        await("handled on a thread of its own").atMost(Duration.ofSeconds(5))
                .until(() -> listener.payloads.equals(List.of(RESPONSE)));
    }

    @Test
    void doesNotRetryWhenTheFirstConnectFails() {
        connectionFactory.connectFailure = new IllegalStateException("Pushca unreachable");

        assertThrows(IllegalStateException.class,
                () -> client.deliverToOrchestrator("orchestrator-device-1", "sequence-1", RESPONSE));

        assertTrue(connectionFactory.connections.isEmpty());
    }

    private ComagentsMessage sentMessage(int index) {
        return ComagentsMessage.fromComagentsMessageString(connectionFactory.connections.getFirst().broadcasts.get(index).message());
    }

    private static final class RecordingListener implements ComagentsMessageListener<AgentContext> {

        final List<AgentContext> payloads = new CopyOnWriteArrayList<>();

        @Override
        public Class<AgentContext> getPayloadClass() {
            return AgentContext.class;
        }

        @Override
        public void consumePayload(String sequenceId, AgentContext payload) {
            payloads.add(payload);
        }
    }
}
