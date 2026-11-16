package com.comagents.agentproxy.agent_response;

import bmv.org.pushca.client.model.ClientFilter;
import com.comagents.agentproxy.agent_setup.AgentSessionWsClientRegistry;
import com.comagents.agentproxy.domain.AgentResponseReady;
import com.comagents.agentproxy.response.AgentSessionResponse;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.json.JsonMapper;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * Hands every agent response to one agent orchestrator as soon as it is on disk: the {@link AgentSessionResponse}, as
 * {@code GET /agent-response} returns it, goes from the session's Pushca client as a find-any broadcast to the
 * orchestrators' clients, so only one of them gets it. Once sent, the session's files are deleted after
 * {@code agent-response.cleanup-delay}, as after a first fetch. When it can't be sent, the response stays to be fetched
 * until {@code agent-response.retention}.
 */
@Singleton
public class AgentResponseBroadcast implements ApplicationEventListener<AgentResponseReady> {

    static final String WORKSPACE_ID = "comagents-main";
    static final String APPLICATION_ID = "AGENT_ORCHESTRATOR";
    static final String ACCOUNT_DOMAIN = "agent-orchestrator@comagents.com";

    // any device of the orchestrator account, only one of them
    static final ClientFilter ORCHESTRATOR = new ClientFilter(WORKSPACE_ID, ACCOUNT_DOMAIN, null, APPLICATION_ID, true, List.of());

    private static final Logger LOG = LoggerFactory.getLogger(AgentResponseBroadcast.class);

    private final AgentResponseService agentResponseService;
    private final AgentSessionWsClientRegistry agentSessionWsClientRegistry;
    private final JsonMapper jsonMapper;

    public AgentResponseBroadcast(AgentResponseService agentResponseService,
                                  AgentSessionWsClientRegistry agentSessionWsClientRegistry, JsonMapper jsonMapper) {
        this.agentResponseService = agentResponseService;
        this.agentSessionWsClientRegistry = agentSessionWsClientRegistry;
        this.jsonMapper = jsonMapper;
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
                if (agentSessionWsClientRegistry.broadcast(event.sessionId(), ORCHESTRATOR, toJson(response))) {
                    LOG.info("[{} {}] response sent to {}", agentName, event.sessionId(), ACCOUNT_DOMAIN);
                    agentResponseService.scheduleCleanup(event.agent(), event.sessionId());
                }
            });
        } catch (RuntimeException e) {
            LOG.error("[{} {}] sending the response to {} failed", agentName, event.sessionId(), ACCOUNT_DOMAIN, e);
        }
    }

    private String toJson(AgentSessionResponse response) {
        try {
            return jsonMapper.writeValueAsString(response);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
