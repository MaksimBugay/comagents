package com.comagents.agentproxy.communication.pushca;

import bmv.org.pushca.client.exception.PushcaCommandException;
import bmv.org.pushca.client.exception.WebsocketConnectionIsBrokenException;
import com.comagents.agentproxy.communication.ComagentsMessageQueue;
import com.comagents.agentproxy.communication.ComagentsQueueConsumer;
import com.comagents.agentproxy.communication.QueuedComagentsMessage;
import com.comagents.agentproxy.communication.QueuedResponseDeliveryService;
import com.comagents.agentproxy.communication.ResponseDeliveryService;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
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
    private final ComagentsMessageQueue queue = new ComagentsMessageQueue();
    // deliveries go through the queue, which the client takes from
    private final ResponseDeliveryService delivery = new QueuedResponseDeliveryService(queue, Duration.ofSeconds(10));
    private final RecordingListener listener = new RecordingListener();
    private final AgentExecutorPushcaClient client = new AgentExecutorPushcaClient(connectionFactory, queue,
            List.of(listener), new AgentProxyConf("0.1", "https://github.com/acme/comagents.git", "token", "key",
            "https://pushca.example", "pushca-key", AiHarnessProvider.CLAUDE_CODE,
            new DeviceIdExtractor("executor-device-1", "host")));

    // as on startup
    @BeforeEach
    void openAndStartSending() {
        client.open();
        client.startSending();
    }

    @AfterEach
    void close() {
        client.close();
    }

    @Test
    void connectsOnceAtStartupAndKeepsTheConnection() {
        assertEquals(1, connectionFactory.connections.size(), "opened at startup");
        delivery.deliverToOrchestrator("orchestrator-device-1", "sequence-1", RESPONSE);
        delivery.deliverToOrchestrator("orchestrator-device-1", "sequence-1", RESPONSE);

        assertEquals(1, connectionFactory.connections.size(), "never opened again while it works");
        assertEquals(2, connectionFactory.connections.getFirst().broadcasts.size());
    }

    @Test
    void retriesEverySecondOnTheSameConnectionUntilItWorksAgain() {
        FakePushcaConnectionFactory.FakeConnection connection = connectionFactory.connections.getFirst();
        connection.broadcastFailure = new WebsocketConnectionIsBrokenException(new IllegalStateException("no ack"));
        // the Pushca client reconnects by itself meanwhile
        Thread.ofVirtual().start(() -> {
            sleep(Duration.ofMillis(1500));
            connection.broadcastFailure = null;
        });

        long start = System.nanoTime();
        delivery.deliverToOrchestrator("orchestrator-device-1", "sequence-1", RESPONSE);

        assertTrue(Duration.ofNanos(System.nanoTime() - start).compareTo(Duration.ofMillis(1500)) >= 0,
                "sent after the connection came back");
        assertEquals(1, connectionFactory.connections.size(), "the same connection");
        ComagentsMessage message = ComagentsMessage.fromComagentsMessageString(connection.broadcasts.getFirst().message());
        assertEquals(RESPONSE, message.comagentsMessagePayload().payload());
    }

    @Test
    void dropsTheMessageWhenTheConnectionStaysBrokenUntilTheResponseTimeout() {
        ResponseDeliveryService shortDelivery = new QueuedResponseDeliveryService(queue, Duration.ofMillis(2500));
        FakePushcaConnectionFactory.FakeConnection connection = connectionFactory.connections.getFirst();
        connection.broadcastFailure = new IllegalStateException(ComagentsQueueConsumer.BROKEN_CONNECTION_MESSAGE);

        long start = System.nanoTime();
        shortDelivery.deliverToOrchestrator("orchestrator-device-1", "sequence-1", RESPONSE);

        // returns without an exception (an error is logged): the orchestrator times out itself and asks again
        assertTrue(Duration.ofNanos(System.nanoTime() - start).compareTo(Duration.ofSeconds(4)) < 0);
        connection.broadcastFailure = null;
        delivery.deliverToOrchestrator("orchestrator-device-1", "sequence-2", RESPONSE);
        assertEquals(1, connection.broadcasts.size(), "the dropped message is never sent");
        assertEquals("sequence-2", sentMessage(0).id());
    }

    @Test
    void doesNotRetryWhatPushcaRejects() {
        PushcaCommandException rejected = new PushcaCommandException("Send message", "no such receiver");
        connectionFactory.connections.getFirst().broadcastFailure = rejected;

        assertSame(rejected, assertThrows(PushcaCommandException.class,
                () -> delivery.deliverToOrchestrator("orchestrator-device-1", "sequence-1", RESPONSE)));
    }

    @Test
    void theMessageIdIsTheGivenSequenceId() {
        delivery.deliverToOrchestrator("orchestrator-device-1", "sequence-1", RESPONSE);

        assertEquals("sequence-1", sentMessage(0).id());
    }

    @Test
    void aResponseWithSequenceIdGoesWithIt() {
        AgentSessionResponse response = new AgentSessionResponse(new StartAgentSessionRequest(UUID.randomUUID(),
                AiAgent.CODE_REVIEW, null, "sequence-1", "orchestrator-device-1", "MBugai"), RESPONSE, 1L,
                "executor-device-1");

        delivery.deliverToOrchestrator("orchestrator-device-1", response);

        assertEquals("sequence-1", sentMessage(0).id());
    }

    @Test
    void aBlankSequenceIdIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> delivery.deliverToOrchestrator("orchestrator-device-1", " ", RESPONSE));
        assertThrows(IllegalArgumentException.class, () -> delivery.deliverErrorToOrchestrator("orchestrator-device-1",
                " ", AgentInfo.class, new IllegalStateException("agent failed")));

        assertTrue(connectionFactory.connections.getFirst().broadcasts.isEmpty());
    }

    @Test
    void deliversAnErrorInPlaceOfTheResponseWithTheRequestsSequenceId() {
        delivery.deliverErrorToOrchestrator("orchestrator-device-1", "sequence-1", AgentInfo.class,
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
        connectionFactory.connections.getFirst().receive(
                ComagentsMessage.ofPayload("sequence-1", RESPONSE, AgentContext.class).toString());

        await("handled on a thread of its own").atMost(Duration.ofSeconds(5))
                .until(() -> listener.payloads.equals(List.of(RESPONSE)));
    }

    @Test
    void closingFailsTheMessagesStillQueued() {
        client.close();
        QueuedComagentsMessage waiting = new QueuedComagentsMessage("orchestrator-device-1",
                ComagentsMessage.ofPayload("sequence-1", RESPONSE, AgentContext.class), Instant.now().plusSeconds(10));
        queue.put(waiting);

        client.close();

        assertTrue(waiting.sent().isCompletedExceptionally());
        assertTrue(connectionFactory.connections.getFirst().broadcasts.isEmpty(), "never sent");
    }

    @SuppressWarnings("SameParameterValue")
    private ComagentsMessage sentMessage(int index) {
        return ComagentsMessage.fromComagentsMessageString(connectionFactory.connections.getFirst().broadcasts.get(index).message());
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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
