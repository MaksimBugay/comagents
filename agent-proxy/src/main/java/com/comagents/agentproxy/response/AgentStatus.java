package com.comagents.agentproxy.response;

import com.comagents.agentproxy.domain.AgentSessionState;
import com.comagents.agentproxy.domain.AiAgent;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.util.UUID;

/**
 * @param startedAt         when the agent run started, in epoch millis
 * @param finishedAt        when it ended, in epoch millis; null while running
 * @param responseAvailable whether {@code GET /agent-response/{agentName}/{sessionId}} has a response to return
 */
@Serdeable
public record AgentStatus(AiAgent aiAgent, UUID sessionId, AgentSessionState state,
                          long startedAt, @Nullable Long finishedAt, boolean responseAvailable) {
}
