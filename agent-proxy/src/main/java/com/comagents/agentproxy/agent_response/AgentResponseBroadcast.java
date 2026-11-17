package com.comagents.agentproxy.agent_response;

import bmv.org.pushca.client.model.ClientFilter;
import com.comagents.agentproxy.agent_setup.AgentSessionWsClientRegistry;
import com.comagents.agentproxy.domain.AgentResponseReady;
import com.comagents.commons.communication.ComagentsMessage;
import com.comagents.commons.response.AgentSessionResponse;
import io.micronaut.context.event.ApplicationEventListener;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Hands every agent response to the orchestrator device that started the session as soon as it is on disk: the
 * {@link AgentSessionResponse}, as {@code GET /agent-response} returns it, as a {@link ComagentsMessage} string, goes
 * from the session's Pushca client as a broadcast to the device named by the start request's
 * {@code orchestratorDeviceId}. Once sent, the session's files are deleted after {@code agent-response.cleanup-delay},
 * as after a first fetch. When it can't be sent, the response stays to be fetched until
 * {@code agent-response.retention}.
 */
@Singleton
public class AgentResponseBroadcast implements ApplicationEventListener<AgentResponseReady> {

    static final String WORKSPACE_ID = "comagents-main";
    static final String APPLICATION_ID = "AGENT_ORCHESTRATOR";
    static final String ACCOUNT_DOMAIN = "agent-orchestrator@comagents.com";

    // the orchestrator device that started the session; one client of it, should it be connected more than once
    static ClientFilter orchestrator(String orchestratorDeviceId) {
        return new ClientFilter(WORKSPACE_ID, ACCOUNT_DOMAIN, orchestratorDeviceId, APPLICATION_ID, true, List.of());
    }

    private static final Logger LOG = LoggerFactory.getLogger(AgentResponseBroadcast.class);

    private final AgentResponseService agentResponseService;
    private final AgentSessionWsClientRegistry agentSessionWsClientRegistry;

    public AgentResponseBroadcast(AgentResponseService agentResponseService,
                                  AgentSessionWsClientRegistry agentSessionWsClientRegistry) {
        this.agentResponseService = agentResponseService;
        this.agentSessionWsClientRegistry = agentSessionWsClientRegistry;
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
                var comagentsMessage = new ComagentsMessage<>(AgentSessionResponse.class, response);
                String orchestratorDeviceId = response.request().orchestratorDeviceId();
                if (agentSessionWsClientRegistry.broadcast(event.sessionId(), orchestrator(orchestratorDeviceId),
                        comagentsMessage.toString())) {
                    LOG.info("[{} {}] response sent to {} device {}", agentName, event.sessionId(), ACCOUNT_DOMAIN,
                            orchestratorDeviceId);
                    agentResponseService.scheduleCleanup(event.agent(), event.sessionId());
                }
            });
        } catch (RuntimeException e) {
            LOG.error("[{} {}] sending the response to {} failed", agentName, event.sessionId(), ACCOUNT_DOMAIN, e);
        }
    }
}
