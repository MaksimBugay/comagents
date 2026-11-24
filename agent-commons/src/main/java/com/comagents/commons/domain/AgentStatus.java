package com.comagents.commons.domain;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * @param startedAt         when the agent run started, in epoch millis
 * @param finishedAt        when it ended, in epoch millis; null while running
 * @param responseAvailable whether an {@code AgentResponseRequest} gets a response
 */
public record AgentStatus(AiAgent aiAgent, UUID sessionId, AgentSessionState state,
                          long startedAt, @Nullable Long finishedAt, boolean responseAvailable) {
}
