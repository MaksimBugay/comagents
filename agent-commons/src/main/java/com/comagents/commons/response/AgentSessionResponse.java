package com.comagents.commons.response;

import com.comagents.commons.domain.agent_context.AgentContext;
import com.comagents.commons.request.StartAgentSessionRequest;

public record AgentSessionResponse(StartAgentSessionRequest request, AgentContext agentContext, long createdAt) {
}
