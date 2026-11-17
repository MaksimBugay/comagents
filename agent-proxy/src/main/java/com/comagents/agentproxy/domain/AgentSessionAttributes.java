package com.comagents.agentproxy.domain;

import com.comagents.agentproxy.agent_setup.AgentSessionLoggingLevel;
import com.comagents.agentproxy.domain.agent_context.AgentContext;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * @param agentContext   what the session works with (see {@code StartAgentSessionRequest}); null for none
 * @param actorAccountId the Pushca account that started the session: the only member of the session's chat
 * @param loggingLevel   what the session's chat shows; {@link AgentSessionLoggingLevel#INFO} when not given
 * @param createdAt      when the start request was created, in epoch milliseconds
 */
@Introspected
public record AgentSessionAttributes(@NotNull UUID sessionId,
                                     @Nullable AgentContext agentContext,
                                     @Nullable String actorAccountId,
                                     AgentSessionLoggingLevel loggingLevel,
                                     long createdAt) {

    public AgentSessionAttributes {
        if (loggingLevel == null) {
            loggingLevel = AgentSessionLoggingLevel.INFO;
        }
    }
}
