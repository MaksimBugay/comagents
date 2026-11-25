package com.comagents.commons.response;

import java.util.UUID;

/**
 * The 202 body of the orchestrator's {@code POST /start}.
 *
 * @param sessionId the started session's id, for {@code GET /agent-response/{sessionId}}; its
 *                  {@code AgentSessionResponse} carries it
 */
public record StartAgentSessionExternalResponse(UUID sessionId) {
}
