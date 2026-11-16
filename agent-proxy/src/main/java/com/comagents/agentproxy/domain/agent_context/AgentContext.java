package com.comagents.agentproxy.domain.agent_context;

import io.micronaut.serde.annotation.Serdeable;

import java.util.Optional;
import java.util.Set;

/**
 * What an agent session gets to work with.
 *
 * @param items at most one required {@link AgentContextItemType#REPOSITORY} item; repositories that are not required
 *              are not limited
 */
@Serdeable
public record AgentContext(Set<AgentContextItem> items) {

    public AgentContext {
        requireAtMostOneRequiredRepository(items);
    }

    /** The required repository: the one cloned as the session's working directory. */
    public Optional<AgentContextItem> requiredRepository() {
        return items == null ? Optional.empty() : items.stream()
                .filter(item -> item.attributes().type() == AgentContextItemType.REPOSITORY && item.attributes().required())
                .findFirst();
    }

    private static void requireAtMostOneRequiredRepository(Set<AgentContextItem> items) {
        if (items == null) {
            return;
        }
        long requiredRepositories = items.stream()
                .filter(item -> item.attributes().type() == AgentContextItemType.REPOSITORY && item.attributes().required())
                .count();
        if (requiredRepositories > 1) {
            throw new IllegalArgumentException("At most one required repository item is allowed, got " + requiredRepositories);
        }
    }
}
