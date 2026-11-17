package com.comagents.agentproxy.communication.pushca;

import bmv.org.pushca.client.model.ClientFilter;
import bmv.org.pushca.client.model.PClient;
import com.comagents.agentproxy.agent_setup.PushcaConnection;
import com.comagents.agentproxy.agent_setup.PushcaConnector;
import com.comagents.agentproxy.communication.ResponseDeliveryService;
import com.comagents.agentproxy.conf.AgentProxyConf;
import com.comagents.commons.communication.ComagentsMessage;
import com.comagents.commons.communication.WithSequenceId;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.event.StartupEvent;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import org.apache.commons.lang3.Validate;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

/**
 * The agent executor's own Pushca client: one per executor (not per session), signed in to the orchestrators'
 * workspace as application {@code <harness>_EXECUTOR} (e.g. {@code CLAUDE_CODE_EXECUTOR},
 * {@link AgentProxyConf#getAiHarnessProvider()}) with the executor's device id ({@link AgentProxyConf#getDeviceId()}).
 * It hands responses to the orchestrator device that asked for them, as {@link ComagentsMessage} strings. The
 * connection is opened once, on service startup, and kept. It is opened again only after an issue: when the startup
 * connect failed, or a delivery finds it broken (once per delivery).
 */
@Singleton
public class AgentExecutorPushcaClient implements ResponseDeliveryService, ApplicationEventListener<StartupEvent> {

    static final String WORKSPACE_ID = "comagents-main";
    static final String APPLICATION_ID_TEMPLATE = "%s_EXECUTOR";
    static final String ACCOUNT_ID = "agent-executor@comagents.com";

    static final String ORCHESTRATOR_APPLICATION_ID = "AGENT_ORCHESTRATOR";
    static final String ORCHESTRATOR_ACCOUNT_ID = "agent-orchestrator@comagents.com";

    private static final Logger LOG = LoggerFactory.getLogger(AgentExecutorPushcaClient.class);

    private final PushcaConnector connector;
    private final PClient client;

    // guarded by this
    private PushcaConnection connection;

    public AgentExecutorPushcaClient(PushcaConnector connector, AgentProxyConf conf) {
        this.connector = connector;
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
    }

    /**
     * Opens the connection unless it is open; when Pushca can't be reached, the next delivery tries again.
     */
    synchronized void open() {
        try {
            if (connection == null) {
                connect();
            }
        } catch (RuntimeException e) {
            LOG.warn("Pushca connection of executor {} not opened, the next delivery tries again: {}", client.deviceId,
                    e.getMessage());
        }
    }

    /**
     * Sends {@code response}, wrapped into a {@link ComagentsMessage}, to the orchestrator device
     * {@code orchestratorDeviceId}; blocks until Pushca has taken it. The message id is the response's
     * {@link WithSequenceId#sequenceId()}, or a new random UUID when it has none.
     *
     * @throws IllegalArgumentException when the device id is blank, or the response null or blank
     * @throws IllegalStateException    when Pushca can't be reached or refuses the message
     */
    @Override
    public synchronized <T> void deliverToOrchestrator(String orchestratorDeviceId, T response) {
        Validate.notBlank(orchestratorDeviceId, "orchestratorDeviceId must not be blank");
        String message = comagentsMessage(response).toString();
        ClientFilter orchestrator = orchestrator(orchestratorDeviceId);
        boolean reused = connection != null;
        try {
            send(orchestrator, message);
        } catch (RuntimeException e) {
            closeConnection();
            if (!reused) {
                throw e;
            }
            // the kept connection may have broken meanwhile: one more try with a new one
            LOG.warn("Pushca connection of executor {} broken ({}), reconnecting", client.deviceId, e.getMessage());
            send(orchestrator, message);
        }
    }

    /**
     * Closes the connection; the next delivery opens a new one.
     */
    @PreDestroy
    public synchronized void close() {
        closeConnection();
    }

    // the orchestrator device that started the session; one client of it, should it be connected more than once
    private static ClientFilter orchestrator(String orchestratorDeviceId) {
        return new ClientFilter(WORKSPACE_ID, ORCHESTRATOR_ACCOUNT_ID, orchestratorDeviceId,
                ORCHESTRATOR_APPLICATION_ID, true, List.of());
    }

    @SuppressWarnings("unchecked")
    private static <T> ComagentsMessage comagentsMessage(T response) {
        Validate.notNull(response, "response must not be null");
        String sequenceId = (response instanceof WithSequenceId responseWithInstanceId) ? responseWithInstanceId.sequenceId() : UUID.randomUUID().toString();
        // the payload's runtime class: the orchestrator reads the message by that class name
        return ComagentsMessage.ofPayload(sequenceId, response, (Class<T>) response.getClass());
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
        connection = connector.connect(client, _ -> {
        });
        LOG.info("Pushca connection of executor {} opened", client.deviceId);
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
