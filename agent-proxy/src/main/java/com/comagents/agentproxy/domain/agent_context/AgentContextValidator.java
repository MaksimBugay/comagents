package com.comagents.agentproxy.domain.agent_context;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.CollectionUtils;
import jakarta.inject.Singleton;
import org.apache.commons.lang3.StringUtils;

import java.util.Set;

/**
 * Checks a start request's agent context against the agent: at most one primary repository, and every item the
 * agent requires ({@code requiredItems} in its {@code agent.json}) present with a value.
 */
@Singleton
public class AgentContextValidator {

    /**
     * @param items         the request's context items; none when null
     * @param requiredItems the items the agent can't work without; none when null
     * @throws InvalidAgentContextException when the context doesn't fit
     */

    public void validate(@Nullable Set<AgentContextItem> items, @Nullable Set<AgentContextItemAttributes> requiredItems) {
        if (CollectionUtils.isEmpty(items) && CollectionUtils.isEmpty(requiredItems)) {
            return;
        }
        requireAtMostOnePrimaryRepository(items);
        if (CollectionUtils.isEmpty(requiredItems)) {
            return;
        }
        validatePresenceOfRequiredItems(items == null ? Set.of() : items, requiredItems);
    }

    private static void requireAtMostOnePrimaryRepository(Set<AgentContextItem> items) {
        if (items == null) {
            return;
        }
        long primaryRepositories = items.stream().filter(AgentContextItem::isPrimaryRepository).count();
        if (primaryRepositories > 1) {
            throw new InvalidAgentContextException("At most one primary repository is allowed, got " + primaryRepositories);
        }
    }

    private static void validatePresenceOfRequiredItems(Set<AgentContextItem> items, Set<AgentContextItemAttributes> requiredItems) {
        for (AgentContextItemAttributes required : requiredItems) {
            boolean present = items.stream()
                    .anyMatch(item -> item.attributes().equals(required) && StringUtils.isNotBlank(item.value()));
            if (!present) {
                throw new InvalidAgentContextException("Required item " + required.type() + " " + required.name()
                        + " is missing or has no value");
            }
        }
    }
}
