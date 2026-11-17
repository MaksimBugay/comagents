package com.comagents.agentproxy.agent_response;

import com.comagents.agentproxy.communication.ResponseDeliveryService;
import com.comagents.agentproxy.domain.AgentResponseReady;
import com.comagents.commons.communication.ComagentsMessage;
import com.comagents.commons.response.AgentSessionResponse;
import io.micronaut.context.event.ApplicationEventListener;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Hands every agent response to the orchestrator device that started the session as soon as it is on disk: the
 * {@link AgentSessionResponse}, as {@code GET /agent-response} returns it, goes as a {@link ComagentsMessage} through
 * the {@link ResponseDeliveryService} (the executor's own Pushca client) to the device named by the start request's
 * {@code orchestratorDeviceId}. Once sent, the session's files are deleted after {@code agent-response.cleanup-delay},
 * as after a first fetch. When it can't be sent, the response stays to be fetched until
 * {@code agent-response.retention}.
 */
@Singleton
public class AgentResponseBroadcast implements ApplicationEventListener<AgentResponseReady> {

    private static final Logger LOG = LoggerFactory.getLogger(AgentResponseBroadcast.class);

    private final AgentResponseService agentResponseService;
    private final ResponseDeliveryService responseDeliveryService;

    public AgentResponseBroadcast(AgentResponseService agentResponseService,
                                  ResponseDeliveryService responseDeliveryService) {
        this.agentResponseService = agentResponseService;
        this.responseDeliveryService = responseDeliveryService;
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
                String orchestratorDeviceId = response.request().orchestratorDeviceId();
                responseDeliveryService.deliverToOrchestrator(orchestratorDeviceId, response);
                LOG.info("[{} {}] response sent to orchestrator device {}", agentName, event.sessionId(),
                        orchestratorDeviceId);
                agentResponseService.scheduleCleanup(event.agent(), event.sessionId());
            });
        } catch (RuntimeException e) {
            LOG.error("[{} {}] sending the response to the orchestrator failed", agentName, event.sessionId(), e);
        }
    }
}
