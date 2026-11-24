package com.comagents.agentorchestrator.controller.response;

import io.micronaut.core.annotation.Introspected;

import java.util.UUID;

/**
 * The 202 body of {@code POST /start}.
 *
 * @param sessionId the started session's id, for {@code GET /agent-response/{sessionId}}
 */
@Introspected
public record StartAgentSessionExternalResponse(UUID sessionId) {
}
