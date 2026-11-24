package com.comagents.agentorchestrator.controller.response;

import com.comagents.commons.domain.agent_context.AgentContext;
import io.micronaut.core.annotation.Introspected;

import java.util.UUID;

/**
 * The 200 body of {@code GET /agent-response/{sessionId}}.
 *
 * @param sessionId    the session that gave the response
 * @param agentContext the agent's final answer (per the agent's {@code responseSchema}, or one {@code STRING} item
 *                     {@code response})
 * @param createdAt    when the response was created, in epoch milliseconds
 */
@Introspected
public record AgentSessionExternalResponse(UUID sessionId, AgentContext agentContext, long createdAt) {
}
