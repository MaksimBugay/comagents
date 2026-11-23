package com.comagents.agentproxy.agent_response;

import com.comagents.agentproxy.domain.AgentResponseReady;
import com.comagents.commons.communication.ComagentsMessage;
import com.comagents.commons.communication.ComagentsMessageDeliveryService;
import com.comagents.commons.communication.DeliveryAddress;
import com.comagents.commons.response.AgentSessionResponse;
import io.micronaut.context.event.ApplicationEventListener;
import jakarta.inject.Singleton;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Hands every agent response to the devices that wait for it as soon as it is on disk: the
 * {@link AgentSessionResponse}, as {@code GET /agent-response} returns it, goes as a {@link ComagentsMessage} through
 * the {@link ComagentsMessageDeliveryService} (the executor's own Pushca client) to the start request's
 * {@code originatorDeviceId} device, then also to its {@code agentResponseWaiterDeviceId} device when there is one,
 * whatever those devices' account and application, with the start request's {@code sequenceId} as the message id.
 * Once sent to all, the session's files are deleted after {@code agent-response.cleanup-delay}, as after a first fetch.
 * When it can't be sent, the response stays to be fetched until {@code agent-response.retention}.
 */
@Singleton
public class AgentResponseBroadcast implements ApplicationEventListener<AgentResponseReady> {

    private static final Logger LOG = LoggerFactory.getLogger(AgentResponseBroadcast.class);

    private final AgentResponseService agentResponseService;
    private final ComagentsMessageDeliveryService messageDeliveryService;

    public AgentResponseBroadcast(AgentResponseService agentResponseService,
                                  ComagentsMessageDeliveryService messageDeliveryService) {
        this.agentResponseService = agentResponseService;
        this.messageDeliveryService = messageDeliveryService;
    }

    // published on the session's output thread, which must not wait for Pushca
    @Override
    public void onApplicationEvent(AgentResponseReady event) {
        Thread.ofVirtual().name("agent-response-" + event.sessionId()).start(() -> send(event));
    }

    void send(AgentResponseReady event) {
        String agentName = event.agent().getAgentName();
        try {
            agentResponseService.readResponse(event.agent(), event.sessionId()).ifPresent(response -> {
                messageDeliveryService.deliverToAddress(
                        DeliveryAddress.deviceWithId(response.request().originatorDeviceId()),
                        response
                );
                LOG.info("[{} {}] response sent to the originator device {}", agentName, event.sessionId(),
                        response.request().originatorDeviceId());
                String agentResponseWaiterDeviceId = response.request().agentResponseWaiterDeviceId();
                if (StringUtils.isNotBlank(agentResponseWaiterDeviceId)) {
                    messageDeliveryService.deliverToAddress(
                            DeliveryAddress.deviceWithId(agentResponseWaiterDeviceId),
                            response
                    );
                    LOG.info("[{} {}] response sent to device {}", agentName, event.sessionId(),
                            agentResponseWaiterDeviceId);
                }
                agentResponseService.scheduleCleanup(event.agent(), event.sessionId());
            });
        } catch (RuntimeException e) {
            LOG.error("[{} {}] sending the response failed", agentName, event.sessionId(), e);
        }
    }
}
