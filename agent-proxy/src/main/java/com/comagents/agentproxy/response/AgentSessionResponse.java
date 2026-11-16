package com.comagents.agentproxy.response;

import com.comagents.agentproxy.domain.agent_context.AgentContext;
import com.comagents.agentproxy.request.StartAgentSessionRequest;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record AgentSessionResponse(StartAgentSessionRequest request, AgentContext agentContext, long createdAt) {
}
