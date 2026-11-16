package com.comagents.agentproxy.domain.agent_context;

import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Set;

/**
 * Checks a start request's agent context against the agent: every item the agent requires ({@code requiredItems} in
 * its {@code agent.json}) must be in it. Everything else about a context is checked by {@link AgentContext} itself.
 */
@Singleton
public class AgentContextValidator {

    /**
     * @param context       the request's context; none when null
     * @param requiredItems the items the agent can't work without
     * @throws InvalidAgentContextException when a required item is missing
     */
    public void validate(@Nullable AgentContext context, Set<RequiredAgentContextItem> requiredItems) {
        List<AgentContextItem> items = context == null ? List.of() : context.items();
        for (RequiredAgentContextItem required : requiredItems) {
            if (items.stream().noneMatch(required::matches)) {
                throw new InvalidAgentContextException("Required " + required + " is missing");
            }
        }
    }
}
