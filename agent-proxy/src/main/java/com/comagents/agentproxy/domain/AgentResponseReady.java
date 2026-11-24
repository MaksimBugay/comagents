package com.comagents.agentproxy.domain;

import com.comagents.commons.domain.AiAgent;

import java.util.UUID;

/**
 * The agent's final answer for the session is on disk: an {@code AgentResponseRequest} gets it, and
 * {@code AgentResponseBroadcast} sends it.
 */
public record AgentResponseReady(AiAgent agent, UUID sessionId) {
}
