package com.comagents.agentproxy.communication;

import com.comagents.agentproxy.agent_response.AgentResponseService;
import com.comagents.commons.communication.ComagentsBaseListener;
import com.comagents.commons.communication.ComagentsMessageDeliveryService;
import com.comagents.commons.request.AgentResponseRequest;
import com.comagents.commons.response.AgentResponseReply;
import jakarta.inject.Singleton;

/**
 * Response requests from an orchestrator over Pushca: its {@code GET /agent-response/{sessionId}} asks here when it has
 * no response yet. The orchestrator device gets back the response, or why there is none: the session is unknown
 * ({@link com.comagents.commons.exception.AgentSessionNotFoundException}), or its response isn't ready yet
 * ({@link com.comagents.commons.exception.AgentResponseNotReadyException}).
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

    // as a first fetch: the session's files are deleted after agent-response.cleanup-delay
    @Override
    protected AgentResponseReply prepareResponse(AgentResponseRequest request) {
        return new AgentResponseReply(agentResponseService.getResponse(request.agent(), request.sessionId()));
    }
}
