package com.comagents.agentproxy.domain;

import java.util.UUID;

/** The agent's final answer for the session is on disk: {@code GET /agent-response/{agentName}/{sessionId}} returns it. */
public record AgentResponseReady(AiAgent agent, UUID sessionId) {
}
