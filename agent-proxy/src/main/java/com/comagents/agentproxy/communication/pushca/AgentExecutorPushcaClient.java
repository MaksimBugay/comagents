package com.comagents.agentproxy.communication.pushca;

import bmv.org.pushca.client.model.ClientFilter;
import bmv.org.pushca.client.model.PClient;
import com.comagents.agentproxy.agent_setup.AgentSessionPrivateChannelService;
import com.comagents.agentproxy.agent_setup.PushcaConnector;
import com.comagents.agentproxy.communication.ResponseDeliveryService;
import com.comagents.agentproxy.conf.AgentProxyConf;
import com.comagents.commons.communication.ComagentsMessage;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.event.StartupEvent;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import org.apache.commons.lang3.Validate;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private AgentSessionPrivateChannelService connection;

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
     * Sends {@code response}, wrapped into a {@link ComagentsMessage} with the id {@code sequenceId}, to the
     * orchestrator device {@code orchestratorDeviceId}; blocks until Pushca has taken it.
     *
     * @throws NullPointerException     when the device id, sequence id or response is null
     * @throws IllegalArgumentException when the device id or sequence id is blank
     * @throws IllegalStateException    when Pushca can't be reached or refuses the message
     */
    @Override
    public <T> void deliverToOrchestrator(String orchestratorDeviceId, String sequenceId, T response) {
        deliver(orchestratorDeviceId, comagentsMessage(sequenceId, response));
    }

    /**
     * Sends {@code error}, wrapped into a {@link ComagentsMessage} of {@code responseClass} with the id
     * {@code sequenceId}, to the orchestrator device {@code orchestratorDeviceId}; blocks until Pushca has taken it.
     *
     * @throws NullPointerException     when the device id, sequence id, response class or error is null
     * @throws IllegalArgumentException when the device id or sequence id is blank
     * @throws IllegalStateException    when Pushca can't be reached or refuses the message
     */
    @Override
    public void deliverErrorToOrchestrator(String orchestratorDeviceId, String sequenceId, Class<?> responseClass,
                                           Throwable error) {
        Validate.notNull(error, "error must not be null");
        deliver(orchestratorDeviceId, ComagentsMessage.ofError(sequenceId, responseClass, error));
    }

    private synchronized void deliver(String orchestratorDeviceId, ComagentsMessage comagentsMessage) {
        Validate.notBlank(orchestratorDeviceId, "orchestratorDeviceId must not be blank");
        String message = comagentsMessage.toString();
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

    // the orchestrator device that sent the request; workspace, account, device and application name exactly one client
    private static ClientFilter orchestrator(String orchestratorDeviceId) {
        return new ClientFilter(WORKSPACE_ID, ORCHESTRATOR_ACCOUNT_ID, orchestratorDeviceId, ORCHESTRATOR_APPLICATION_ID);
    }

    @SuppressWarnings("unchecked")
    private static <T> ComagentsMessage comagentsMessage(String sequenceId, T response) {
        Validate.notNull(response, "response must not be null");
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
