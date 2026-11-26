package com.comagents.agentproxy.communication.pushca;

import bmv.org.pushca.client.exception.PushcaCommandException;
import bmv.org.pushca.client.exception.WebsocketConnectionIsBrokenException;
import bmv.org.pushca.client.exception.WebsocketConnectionIsNotOpenException;
import bmv.org.pushca.client.model.ClientFilter;
import com.comagents.agentproxy.conf.AgentProxyConf;
import com.comagents.commons.communication.ComagentsMessage;
import com.comagents.commons.communication.ComagentsMessageDeliveryService;
import com.comagents.commons.communication.ComagentsMessageListener;
import com.comagents.commons.communication.ComagentsMessageQueue;
import com.comagents.commons.communication.DeliveryAddress;
import com.comagents.commons.communication.DeviceIdExtractor;
import com.comagents.commons.communication.QueuedComagentsMessage;
import com.comagents.commons.communication.QueuedComagentsMessageDeliveryService;
import com.comagents.commons.domain.AiAgent;
import com.comagents.commons.domain.AiHarnessProvider;
import com.comagents.commons.domain.agent_context.AgentContext;
import com.comagents.commons.domain.agent_context.StringAgentContextItem;
import com.comagents.commons.request.StartAgentSessionRequest;
import com.comagents.commons.response.AgentInfoResponse;
import com.comagents.commons.response.AgentSessionResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.comagents.commons.communication.pushca.ClientFilterUtils.COMAGENTS_WORKSPACE_ID;
import static com.comagents.commons.communication.CommunicationUtils.ORCHESTRATOR_ACCOUNT_ID;
import static com.comagents.commons.communication.CommunicationUtils.ORCHESTRATOR_APPLICATION_ID;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentExecutorPushcaClientTest {

    private static final AgentContext RESPONSE =
            new AgentContext(List.of(new StringAgentContextItem("response", "done")));
    private static final DeliveryAddress ORCHESTRATOR = DeliveryAddress.agentOrchestrator("orchestrator-device-1");
    private static final ClientFilter ORCHESTRATOR_CLIENTS = new ClientFilter(COMAGENTS_WORKSPACE_ID,
            ORCHESTRATOR_ACCOUNT_ID, "orchestrator-device-1", ORCHESTRATOR_APPLICATION_ID);

    private final FakePushcaConnectionFactory connectionFactory = new FakePushcaConnectionFactory();
    private final ComagentsMessageQueue queue = new ComagentsMessageQueue();
    // deliveries go through the queue, which the client takes from
    private final ComagentsMessageDeliveryService delivery =
            new QueuedComagentsMessageDeliveryService(queue, Duration.ofSeconds(10));
    private final RecordingListener listener = new RecordingListener();
    private final AgentExecutorPushcaClient client = new AgentExecutorPushcaClient(connectionFactory, queue,
            List.of(listener), new AgentProxyConf("0.1", "https://github.com/acme/comagents.git", "token",
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
        deliverToOrchestrator(delivery, "sequence-1");
        deliverToOrchestrator(delivery, "sequence-1");

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
        deliverToOrchestrator(delivery, "sequence-1");

        assertTrue(Duration.ofNanos(System.nanoTime() - start).compareTo(Duration.ofMillis(1500)) >= 0,
                "sent after the connection came back");
        assertEquals(1, connectionFactory.connections.size(), "the same connection");
        ComagentsMessage message =
                ComagentsMessage.fromComagentsMessageString(connection.broadcasts.getFirst().message());
        assertEquals(RESPONSE, message.comagentsMessagePayload().payload());
    }

    @Test
    void dropsTheMessageWhenTheConnectionStaysBrokenUntilTheMessageTimeout() {
        ComagentsMessageDeliveryService shortDelivery =
                new QueuedComagentsMessageDeliveryService(queue, Duration.ofMillis(2500));
        FakePushcaConnectionFactory.FakeConnection connection = connectionFactory.connections.getFirst();
        connection.broadcastFailure = new WebsocketConnectionIsNotOpenException();

        long start = System.nanoTime();
        deliverToOrchestrator(shortDelivery, "sequence-1");

        // returns without an exception (an error is logged): the orchestrator times out itself and asks again
        assertTrue(Duration.ofNanos(System.nanoTime() - start).compareTo(Duration.ofSeconds(4)) < 0);
        connection.broadcastFailure = null;
        deliverToOrchestrator(delivery, "sequence-2");
        assertEquals(1, connection.broadcasts.size(), "the dropped message is never sent");
        assertEquals("sequence-2", sentMessage(0).id());
    }

    @Test
    void doesNotRetryWhatPushcaRejects() {
        PushcaCommandException rejected = new PushcaCommandException("Send message", "no such receiver");
        connectionFactory.connections.getFirst().broadcastFailure = rejected;

        assertSame(rejected, assertThrows(PushcaCommandException.class,
                () -> deliverToOrchestrator(delivery, "sequence-1")));
    }

    @Test
    void theMessageIdIsTheGivenSequenceId() {
        deliverToOrchestrator(delivery, "sequence-1");

        assertEquals("sequence-1", sentMessage(0).id());
    }

    @Test
    void sendsToTheGivenAccountDeviceAndApplication() {
        delivery.deliverToAddress(new DeliveryAddress("someone@comagents.com", "device-1", "CHAT_ROOM"), "sequence-1",
                RESPONSE);

        ClientFilter receivers = connectionFactory.connections.getFirst().broadcasts.getFirst().receivers();
        assertEquals(new ClientFilter(COMAGENTS_WORKSPACE_ID, "someone@comagents.com", "device-1", "CHAT_ROOM"),
                receivers);
    }

    @Test
    void blankIdsGoToPushcaAsNull() {
        delivery.deliverToAddress(new DeliveryAddress(" ", "device-1", ""), "sequence-1", RESPONSE);

        ClientFilter receivers = connectionFactory.connections.getFirst().broadcasts.getFirst().receivers();
        assertEquals(new ClientFilter(COMAGENTS_WORKSPACE_ID, null, "device-1", null), receivers);
    }

    @Test
    void aMissingAddressIsRejected() {
        assertThrows(NullPointerException.class, () -> delivery.deliverToAddress(null, "sequence-1", RESPONSE));
        assertThrows(NullPointerException.class, () -> delivery.deliverErrorToAddress(null, "sequence-1",
                AgentInfoResponse.class, new IllegalStateException("agent failed")));

        assertTrue(connectionFactory.connections.getFirst().broadcasts.isEmpty());
    }

    @Test
    void sendsToTheOrchestratorApplication() {
        deliverToOrchestrator(delivery, "sequence-1");

        ClientFilter receivers = connectionFactory.connections.getFirst().broadcasts.getFirst().receivers();
        assertEquals(ORCHESTRATOR_CLIENTS, receivers);
    }

    @Test
    void aResponseWithSequenceIdGoesWithIt() {
        AgentSessionResponse response = new AgentSessionResponse(new StartAgentSessionRequest(UUID.randomUUID(),
                AiAgent.CODE_REVIEW, null, "sequence-1", "orchestrator-device-1", "MBugai"), RESPONSE, 1L,
                "executor-device-1");

        delivery.deliverToAddress(ORCHESTRATOR, response);

        assertEquals("sequence-1", sentMessage(0).id());
    }

    @Test
    void aBlankSequenceIdIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> deliverToOrchestrator(delivery, " "));
        assertThrows(IllegalArgumentException.class, () -> deliverErrorToOrchestrator(" "));

        assertTrue(connectionFactory.connections.getFirst().broadcasts.isEmpty());
    }

    @Test
    void deliversAnErrorInPlaceOfTheResponseWithTheRequestsSequenceId() {
        deliverErrorToOrchestrator("sequence-1");

        ComagentsMessage message = sentMessage(0);
        assertEquals("sequence-1", message.id());
        assertEquals(AgentInfoResponse.class.getName(), message.payloadClassName());
        assertNull(message.payloadString());
        assertEquals(IllegalStateException.class.getName(), message.error().errorClassName());
        assertEquals("agent failed", message.error().errorMessage());
        assertEquals(ORCHESTRATOR_CLIENTS, connectionFactory.connections.getFirst().broadcasts.getFirst().receivers());
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
        QueuedComagentsMessage waiting = new QueuedComagentsMessage(ORCHESTRATOR,
                ComagentsMessage.ofPayload("sequence-1", RESPONSE, AgentContext.class), Instant.now().plusSeconds(10));
        queue.put(waiting);

        client.close();

        assertTrue(waiting.sent().isCompletedExceptionally());
        assertTrue(connectionFactory.connections.getFirst().broadcasts.isEmpty(), "never sent");
    }

    private static void deliverToOrchestrator(ComagentsMessageDeliveryService delivery, String sequenceId) {
        delivery.deliverToAddress(ORCHESTRATOR, sequenceId, RESPONSE);
    }

    private void deliverErrorToOrchestrator(String sequenceId) {
        delivery.deliverErrorToAddress(ORCHESTRATOR, sequenceId, AgentInfoResponse.class,
                new IllegalStateException("agent failed"));
    }

    @SuppressWarnings("SameParameterValue")
    private ComagentsMessage sentMessage(int index) {
        String sent = connectionFactory.connections.getFirst().broadcasts.get(index).message();
        return ComagentsMessage.fromComagentsMessageString(sent);
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
