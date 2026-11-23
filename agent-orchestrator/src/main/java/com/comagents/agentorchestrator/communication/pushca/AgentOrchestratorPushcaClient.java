package com.comagents.agentorchestrator.communication.pushca;

import bmv.org.pushca.client.PushcaWebSocket;
import bmv.org.pushca.client.model.PClient;
import com.comagents.agentorchestrator.conf.AgentOrchestratorConf;
import com.comagents.commons.communication.ComagentsMessage;
import com.comagents.commons.communication.ComagentsMessageDispatcher;
import com.comagents.commons.communication.ComagentsMessageListener;
import com.comagents.commons.communication.ComagentsMessageQueue;
import com.comagents.commons.communication.ComagentsQueueConsumer;
import com.comagents.commons.communication.QueuedComagentsMessage;
import com.comagents.commons.communication.pushca.ClientFilterUtils;
import com.comagents.commons.communication.pushca.PushcaConnectionFactory;
import com.comagents.commons.communication.pushca.PushcaConnectionFailures;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.event.StartupEvent;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import org.apache.commons.lang3.Validate;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static com.comagents.commons.communication.CommunicationUtils.ORCHESTRATOR_ACCOUNT_ID;
import static com.comagents.commons.communication.CommunicationUtils.ORCHESTRATOR_APPLICATION_ID;
import static com.comagents.commons.communication.pushca.ClientFilterUtils.COMAGENTS_WORKSPACE_ID;
import static com.comagents.commons.communication.pushca.PushcaConnectionFactory.IGNORE_MESSAGES;

/**
 * The orchestrator's own Pushca client: one per orchestrator, signed in to the comagents workspace as account
 * {@code agent-orchestrator@comagents.com}, application {@code AGENT_ORCHESTRATOR}, with the orchestrator's device id
 * ({@link AgentOrchestratorConf#getDeviceId()}). It passes the agent executors' messages (their discovery
 * {@code AgentInfoResponse}s, replies and agent responses) to the {@link ComagentsMessageListener}s, and sends the queued
 * {@link ComagentsMessage}s ({@link ComagentsMessageQueue}) one by one to the address each is for. The connection is
 * opened once, on service startup; the Pushca client signs in and reconnects by itself whenever it isn't open. Sends
 * meanwhile are retried until their message expires ({@link #consumeWithRetry}).
 */
@Singleton
public class AgentOrchestratorPushcaClient
        implements ComagentsQueueConsumer, ApplicationEventListener<StartupEvent>, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(AgentOrchestratorPushcaClient.class);

    private final PushcaConnectionFactory connectionFactory;
    private final ComagentsMessageQueue queue;
    private final ComagentsMessageDispatcher dispatcher;
    private final PClient client;

    // guarded by this
    private PushcaWebSocket connection;
    private Thread sender;

    public AgentOrchestratorPushcaClient(
            PushcaConnectionFactory connectionFactory, ComagentsMessageQueue queue,
            // all listeners: a List injection, which the IDE's Micronaut inspection doesn't recognize for a
            // wildcard type
            @SuppressWarnings("MnInjectionPoints") List<ComagentsMessageListener<?>> listeners,
            AgentOrchestratorConf conf) {
        this.connectionFactory = connectionFactory;
        this.queue = queue;
        this.dispatcher = new ComagentsMessageDispatcher(listeners);
        this.client = new PClient(
                COMAGENTS_WORKSPACE_ID,
                ORCHESTRATOR_ACCOUNT_ID,
                conf.getDeviceId(),
                ORCHESTRATOR_APPLICATION_ID
        );
    }

    @Override
    public void onApplicationEvent(@NonNull StartupEvent event) {
        open();
        startSending();
    }

    /**
     * Opens the connection, unless it is open; from then on the Pushca client keeps it open by itself.
     */
    synchronized void open() {
        if (connection == null) {
            // the orchestrator receives nothing on channels
            connection = connectionFactory.createConnection(client, this::onComagentsMessage, IGNORE_MESSAGES);
            LOG.info("Pushca client of orchestrator {} started", client.deviceId);
        }
    }

    /** Starts taking the queued messages, unless already started; {@link #close()} stops it. */
    synchronized void startSending() {
        if (sender == null) {
            sender = Thread.ofVirtual().name("pushca-orchestrator-sender").start(() -> consumeAll(queue));
        }
    }

    /**
     * Sends {@code message} to the Pushca clients of its address over the kept connection.
     *
     * @throws RuntimeException when it can't be sent: the connection isn't open or broke, or Pushca refuses it
     */
    @Override
    public synchronized void consume(QueuedComagentsMessage message) {
        Validate.validState(connection != null, "Pushca connection of orchestrator %s not open", client.deviceId);
        connection.broadcastMessage(ClientFilterUtils.of(message.address()), message.message().toString());
    }

    @Override
    public boolean isBrokenConnection(RuntimeException e) {
        return PushcaConnectionFailures.isBrokenConnection(e);
    }

    /**
     * Stops sending, fails the messages still queued and closes the connection.
     */
    @PreDestroy
    @Override
    public void close() {
        Thread stopped;
        synchronized (this) {
            stopped = sender;
            sender = null;
        }
        if (stopped != null) {
            stopped.interrupt();
        }
        queue.drain().forEach(message -> message.sent().completeExceptionally(
                new IllegalStateException("Pushca client of orchestrator " + client.deviceId + " closed")));
        closeConnection();
    }

    // on the Pushca client's receiving thread, which must not wait for the listeners
    private void onComagentsMessage(String comagentsMessageString) {
        Thread.ofVirtual().name("comagents-message").start(() -> dispatch(comagentsMessageString));
    }

    // a listener handles its own failures; what fails here has no one to go to
    private void dispatch(String comagentsMessageString) {
        try {
            dispatcher.dispatch(comagentsMessageString);
        } catch (RuntimeException e) {
            LOG.error("Comagents message not handled", e);
        }
    }

    private synchronized void closeConnection() {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (RuntimeException e) {
            LOG.debug("Closing the Pushca connection of orchestrator {} failed: {}", client.deviceId, e.getMessage());
        } finally {
            connection = null;
        }
    }
}
