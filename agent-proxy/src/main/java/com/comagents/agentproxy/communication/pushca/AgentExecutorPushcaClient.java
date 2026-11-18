package com.comagents.agentproxy.communication.pushca;

import bmv.org.pushca.client.PushcaWebSocket;
import bmv.org.pushca.client.model.ClientFilter;
import bmv.org.pushca.client.model.PClient;
import com.comagents.agentproxy.communication.ComagentsMessageQueue;
import com.comagents.agentproxy.communication.ComagentsQueueConsumer;
import com.comagents.agentproxy.communication.QueuedComagentsMessage;
import com.comagents.agentproxy.conf.AgentProxyConf;
import com.comagents.commons.communication.ComagentsMessage;
import com.comagents.commons.communication.ComagentsMessageDispatcher;
import com.comagents.commons.communication.ComagentsMessageListener;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.event.StartupEvent;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static com.comagents.agentproxy.communication.pushca.PushcaConnectionFactory.IGNORE_MESSAGES;

/**
 * The agent executor's own Pushca client: one per executor (not per session), signed in to the orchestrators'
 * workspace as application {@code <harness>_EXECUTOR} (e.g. {@code CLAUDE_CODE_EXECUTOR},
 * {@link AgentProxyConf#getAiHarnessProvider()}) with the executor's device id ({@link AgentProxyConf#getDeviceId()}).
 * It passes the orchestrators' requests to the {@link ComagentsMessageListener}s, and sends the queued
 * {@link ComagentsMessage}s ({@link ComagentsMessageQueue}) one by one to the orchestrator device each is for. The
 * connection is opened once, on service startup, and kept. It is opened again only after an issue: when the startup
 * connect failed, or a send finds it broken (once per message).
 */
@Singleton
public class AgentExecutorPushcaClient
        implements ComagentsQueueConsumer, ApplicationEventListener<StartupEvent>, AutoCloseable {

    static final String WORKSPACE_ID = "comagents-main";
    static final String APPLICATION_ID_TEMPLATE = "%s_EXECUTOR";
    static final String ACCOUNT_ID = "agent-executor@comagents.com";

    static final String ORCHESTRATOR_APPLICATION_ID = "AGENT_ORCHESTRATOR";
    static final String ORCHESTRATOR_ACCOUNT_ID = "agent-orchestrator@comagents.com";

    private static final Logger LOG = LoggerFactory.getLogger(AgentExecutorPushcaClient.class);

    private final PushcaConnectionFactory connectionFactory;
    private final ComagentsMessageQueue queue;
    private final ComagentsMessageDispatcher dispatcher;
    private final PClient client;

    // guarded by this
    private PushcaWebSocket connection;
    private Thread sender;

    public AgentExecutorPushcaClient(
            PushcaConnectionFactory connectionFactory, ComagentsMessageQueue queue,
            // all listeners: a List injection, which the IDE's Micronaut inspection doesn't recognize for a
            // wildcard type
            @SuppressWarnings("MnInjectionPoints") List<ComagentsMessageListener<?>> listeners,
            AgentProxyConf conf) {
        this.connectionFactory = connectionFactory;
        this.queue = queue;
        this.dispatcher = new ComagentsMessageDispatcher(listeners);
        this.client = new PClient(
                WORKSPACE_ID,
                ACCOUNT_ID,
                conf.getDeviceId(),
                APPLICATION_ID_TEMPLATE.formatted(conf.getAiHarnessProvider().name())
        );
    }

    @Override
    public void onApplicationEvent(@NonNull StartupEvent event) {
        open();
        startSending();
    }

    /**
     * Opens the connection unless it is open; when Pushca can't be reached, the next send tries again.
     */
    synchronized void open() {
        try {
            if (connection == null) {
                connect();
            }
        } catch (RuntimeException e) {
            LOG.warn("Pushca connection of executor {} not opened, the next send tries again: {}", client.deviceId,
                    e.getMessage());
        }
    }

    /** Starts taking the queued messages, unless already started; {@link #close()} stops it. */
    synchronized void startSending() {
        if (sender == null) {
            sender = Thread.ofVirtual().name("pushca-executor-sender").start(() -> consumeAll(queue));
        }
    }

    /**
     * Sends {@code message} to its orchestrator device over the kept connection; a broken connection is replaced
     * once.
     *
     * @throws IllegalStateException when Pushca can't be reached or refuses the message
     */
    @Override
    public synchronized void consume(QueuedComagentsMessage message) {
        String messageString = message.message().toString();
        ClientFilter orchestrator = orchestrator(message.orchestratorDeviceId());
        boolean reused = connection != null;
        try {
            send(orchestrator, messageString);
        } catch (RuntimeException e) {
            closeConnection();
            if (!reused) {
                throw e;
            }
            // the kept connection may have broken meanwhile: one more try with a new one
            LOG.warn("Pushca connection of executor {} broken ({}), reconnecting", client.deviceId, e.getMessage());
            send(orchestrator, messageString);
        }
    }

    /**
     * Closes the connection; the next message sent opens a new one.
     */
    public synchronized void disconnect() {
        closeConnection();
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
                new IllegalStateException("Pushca client of executor " + client.deviceId + " closed")));
        disconnect();
    }

    // the orchestrator device that sent the request; workspace, account, device and application name exactly one
    // client
    private static ClientFilter orchestrator(String orchestratorDeviceId) {
        return new ClientFilter(WORKSPACE_ID, ORCHESTRATOR_ACCOUNT_ID, orchestratorDeviceId, ORCHESTRATOR_APPLICATION_ID);
    }

    // over the kept connection; there is none only after an issue (startup connect failed, or it broke)
    private void send(ClientFilter receivers, String message) {
        if (connection == null) {
            connect();
        }
        connection.broadcastMessage(receivers, message);
    }

    private void connect() {
        // the executor receives nothing on channels
        connection = connectionFactory.createConnection(client, this::onComagentsMessage, IGNORE_MESSAGES);
        LOG.info("Pushca connection of executor {} opened", client.deviceId);
    }

    // on the Pushca client's receiving thread, which must not wait: a start request clones and copies files
    private void onComagentsMessage(String comagentsMessageString) {
        Thread.ofVirtual().name("comagents-message").start(() -> dispatch(comagentsMessageString));
    }

    // a listener answers the orchestrator itself, errors included; what fails here has no one to go to
    private void dispatch(String comagentsMessageString) {
        try {
            dispatcher.dispatch(comagentsMessageString);
        } catch (RuntimeException e) {
            LOG.error("Comagents message not handled", e);
        }
    }

    private void closeConnection() {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (RuntimeException e) {
            LOG.debug("Closing the Pushca connection of executor {} failed: {}", client.deviceId, e.getMessage());
        } finally {
            connection = null;
        }
    }
}
