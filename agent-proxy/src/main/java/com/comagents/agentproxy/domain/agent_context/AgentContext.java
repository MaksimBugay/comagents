package com.comagents.agentproxy.domain.agent_context;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.util.Optional;
import java.util.Set;

/**
 * What an agent session gets to work with.
 *
 * @param items the context's items, with at most one primary {@link AgentContextItemType#REPOSITORY}: the one
 *              cloned as the session's working directory. Empty when null
 */
@Serdeable
public record AgentContext(@Nullable Set<AgentContextItem> items) {

    public AgentContext {
        items = items == null ? Set.of() : Set.copyOf(items);
    }

    /**
     * The primary repository: the one cloned as the session's working directory.
     */
    public Optional<AgentContextItem> primaryRepository() {
        return items.stream().filter(AgentContextItem::isPrimaryRepository).findFirst();
    }

}
