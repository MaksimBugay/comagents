package com.comagents.agentorchestrator.communication.pushca;

import bmv.org.pushca.client.exception.PushcaCommandException;
import bmv.org.pushca.client.exception.WebsocketConnectionIsBrokenException;
import bmv.org.pushca.client.exception.WebsocketConnectionIsNotOpenException;
import bmv.org.pushca.client.model.ClientFilter;
import bmv.org.pushca.client.model.PClient;
import com.comagents.agentorchestrator.conf.AgentOrchestratorConf;
import com.comagents.commons.communication.ComagentsMessage;
import com.comagents.commons.communication.ComagentsMessageDeliveryService;
import com.comagents.commons.communication.ComagentsMessageListener;
import com.comagents.commons.communication.ComagentsMessageQueue;
import com.comagents.commons.communication.DeliveryAddress;
import com.comagents.commons.communication.DeviceIdExtractor;
import com.comagents.commons.communication.QueuedComagentsMessage;
import com.comagents.commons.communication.QueuedComagentsMessageDeliveryService;
import com.comagents.commons.domain.AiHarnessProvider;
import com.comagents.commons.domain.agent_context.AgentContext;
import com.comagents.commons.domain.agent_context.StringAgentContextItem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.comagents.commons.communication.CommunicationUtils.AGENT_EXECUTOR_ACCOUNT_ID;
import static com.comagents.commons.communication.CommunicationUtils.ORCHESTRATOR_ACCOUNT_ID;
import static com.comagents.commons.communication.CommunicationUtils.ORCHESTRATOR_APPLICATION_ID;
import static com.comagents.commons.communication.CommunicationUtils.buildAgentExecutorApplicationId;
import static com.comagents.commons.communication.pushca.ClientFilterUtils.COMAGENTS_WORKSPACE_ID;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentOrchestratorPushcaClientTest {

    private static final AgentContext PAYLOAD =
            new AgentContext(List.of(new StringAgentContextItem("request", "review")));
    private static final DeliveryAddress EXECUTOR =
            DeliveryAddress.agentExecutor("executor-device-1", AiHarnessProvider.CLAUDE_CODE);
    private static final ClientFilter EXECUTOR_CLIENTS = new ClientFilter(COMAGENTS_WORKSPACE_ID,
            AGENT_EXECUTOR_ACCOUNT_ID, "executor-device-1", buildAgentExecutorApplicationId(AiHarnessProvider.CLAUDE_CODE));

    private final FakePushcaConnectionFactory connectionFactory = new FakePushcaConnectionFactory();
    private final ComagentsMessageQueue queue = new ComagentsMessageQueue();
    // deliveries go through the queue, which the client takes from
    private final ComagentsMessageDeliveryService delivery =
            new QueuedComagentsMessageDeliveryService(queue, Duration.ofSeconds(10));
    private final RecordingListener listener = new RecordingListener();
    private final AgentOrchestratorPushcaClient client = new AgentOrchestratorPushcaClient(connectionFactory, queue,
            List.of(listener), new AgentOrchestratorConf("0.1", "https://pushca.example", "pushca-key",
            new DeviceIdExtractor("orchestrator-device-1", "host")));

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
    void signsInAsTheOrchestratorWithItsDeviceId() {
        assertEquals(new PClient(COMAGENTS_WORKSPACE_ID, ORCHESTRATOR_ACCOUNT_ID, "orchestrator-device-1",
                ORCHESTRATOR_APPLICATION_ID), connectionFactory.connections.getFirst().client);
    }

    @Test
    void connectsOnceAtStartupAndKeepsTheConnection() {
        client.open();
        deliverToExecutor(delivery, "sequence-1");
        deliverToExecutor(delivery, "sequence-2");

        assertEquals(1, connectionFactory.connections.size(), "never opened again while it works");
        assertEquals(2, connectionFactory.connections.getFirst().broadcasts.size());
    }

    @Test
    void sendsToTheExecutorWithTheSequenceIdAsMessageId() {
        deliverToExecutor(delivery, "sequence-1");

        FakePushcaConnectionFactory.Broadcast sent = connectionFactory.connections.getFirst().broadcasts.getFirst();
        assertEquals(EXECUTOR_CLIENTS, sent.receivers());
        ComagentsMessage message = ComagentsMessage.fromComagentsMessageString(sent.message());
        assertEquals("sequence-1", message.id());
        assertEquals(PAYLOAD, message.comagentsMessagePayload().payload());
    }

    @Test
    void retriesOnTheSameConnectionUntilItWorksAgain() {
        FakePushcaConnectionFactory.FakeConnection connection = connectionFactory.connections.getFirst();
        connection.broadcastFailure = new WebsocketConnectionIsBrokenException(new IllegalStateException("no ack"));
        // the Pushca client reconnects by itself meanwhile
        Thread.ofVirtual().start(() -> {
            sleep(Duration.ofMillis(1500));
            connection.broadcastFailure = null;
        });

        deliverToExecutor(delivery, "sequence-1");

        assertEquals(1, connectionFactory.connections.size(), "the same connection");
        assertEquals(1, connection.broadcasts.size());
    }

    @Test
    void dropsTheMessageWhenTheConnectionStaysBrokenUntilTheMessageTimeout() {
        ComagentsMessageDeliveryService shortDelivery =
                new QueuedComagentsMessageDeliveryService(queue, Duration.ofMillis(2500));
        FakePushcaConnectionFactory.FakeConnection connection = connectionFactory.connections.getFirst();
        connection.broadcastFailure = new WebsocketConnectionIsNotOpenException();

        deliverToExecutor(shortDelivery, "sequence-1");

        connection.broadcastFailure = null;
        deliverToExecutor(delivery, "sequence-2");
        assertEquals(1, connection.broadcasts.size(), "the dropped message is never sent");
    }

    @Test
    void doesNotRetryWhatPushcaRejects() {
        PushcaCommandException rejected = new PushcaCommandException("Send message", "no such receiver");
        connectionFactory.connections.getFirst().broadcastFailure = rejected;

        assertSame(rejected, assertThrows(PushcaCommandException.class,
                () -> deliverToExecutor(delivery, "sequence-1")));
    }

    @Test
    void passesTheExecutorsMessagesToTheirListeners() {
        connectionFactory.connections.getFirst().receive(
                ComagentsMessage.ofPayload("sequence-1", PAYLOAD, AgentContext.class).toString());

        await("handled on a thread of its own").atMost(Duration.ofSeconds(5))
                .until(() -> listener.payloads.equals(List.of(PAYLOAD)));
    }

    @Test
    void closingFailsTheMessagesStillQueuedAndClosesTheConnection() {
        client.close();
        QueuedComagentsMessage waiting = new QueuedComagentsMessage(EXECUTOR,
                ComagentsMessage.ofPayload("sequence-1", PAYLOAD, AgentContext.class), Instant.now().plusSeconds(10));
        queue.put(waiting);

        client.close();

        assertTrue(waiting.sent().isCompletedExceptionally());
        assertTrue(connectionFactory.connections.getFirst().broadcasts.isEmpty(), "never sent");
        assertTrue(connectionFactory.connections.getFirst().closed);
    }

    private static void deliverToExecutor(ComagentsMessageDeliveryService delivery, String sequenceId) {
        delivery.deliverToAddress(EXECUTOR, sequenceId, PAYLOAD);
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
