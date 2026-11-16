package com.comagents.agentproxy.domain.agent_context;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

/**
 * An item an agent can't work without ({@code requiredItems} in its {@code agent.json}): any item of that type and
 * name. Without a type any item of that name matches, without a name any item of that type. Names ignore case.
 */
@Serdeable
public record RequiredAgentContextItem(@Nullable AgentContextItemType type, @Nullable String name) {

    public boolean matches(AgentContextItem item) {
        return (type == null || type == item.type()) && (name == null || item.isNamed(name));
    }

    @SuppressWarnings("NullableProblems")
    @Override
    public @NonNull String toString() {
        return (type == null ? "item" : type + " item") + (name == null ? "" : " " + name);
    }
}
