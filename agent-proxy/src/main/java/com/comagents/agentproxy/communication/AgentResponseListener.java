package com.comagents.agentproxy.communication;

import com.comagents.agentproxy.agent_response.AgentResponseService;
import com.comagents.commons.request.AgentResponseRequest;
import com.comagents.commons.response.AgentResponseReply;
import jakarta.inject.Singleton;

/**
 * Response requests from an orchestrator over Pushca, as {@code GET /agent-response/{agentName}/{sessionId}}; the
 * orchestrator device gets back the response, or why there is none: the session is unknown
 * ({@link com.comagents.agentproxy.agent_status.AgentSessionNotFoundException}), or its response isn't ready yet
 * ({@link com.comagents.agentproxy.agent_response.AgentResponseNotReadyException}).
 */
@Singleton
public class AgentResponseListener extends ComagentsBaseListener<AgentResponseRequest, AgentResponseReply> {

    private final AgentResponseService agentResponseService;

    public AgentResponseListener(AgentResponseService agentResponseService,
                                 ComagentsMessageDeliveryService messageDeliveryService) {
        super(messageDeliveryService);
        this.agentResponseService = agentResponseService;
    }

    @Override
    public Class<AgentResponseRequest> getPayloadClass() {
        return AgentResponseRequest.class;
    }

    @Override
    protected Class<AgentResponseReply> getResponseClass() {
        return AgentResponseReply.class;
    }

    // as a fetch over HTTP: the session's files are deleted after agent-response.cleanup-delay
    @Override
    protected AgentResponseReply prepareResponse(AgentResponseRequest request) {
        return new AgentResponseReply(agentResponseService.getResponse(request.agent(), request.sessionId()));
    }
}
