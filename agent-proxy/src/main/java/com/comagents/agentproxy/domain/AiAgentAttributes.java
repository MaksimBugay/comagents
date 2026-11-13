package com.comagents.agentproxy.domain;

import com.comagents.agentproxy.agent_setup.AgentSessionLoggingLevel;
import io.micronaut.core.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * @param actorAccountId the Pushca account that started the session: the only member of the session's chat
 * @param loggingLevel   what the session's chat shows; {@link AgentSessionLoggingLevel#INFO} when not given
 */
public record AiAgentAttributes(@NotNull UUID sessionId,
                                String cloneSourceUrl,
                                @Nullable String actorAccountId,
                                AgentSessionLoggingLevel loggingLevel) {

    public AiAgentAttributes {
        if (loggingLevel == null) {
            loggingLevel = AgentSessionLoggingLevel.INFO;
        }
    }
}
