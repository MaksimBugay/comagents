package com.comagents.commons.response;

import com.comagents.commons.domain.AgentSessionState;
import com.comagents.commons.domain.AiAgent;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * @param startedAt         when the agent run started, in epoch millis
 * @param finishedAt        when it ended, in epoch millis; null while running
 * @param responseAvailable whether {@code GET /agent-response/{agentName}/{sessionId}} has a response to return
 */
public record AgentStatus(AiAgent aiAgent, UUID sessionId, AgentSessionState state,
                          long startedAt, @Nullable Long finishedAt, boolean responseAvailable) {
}
