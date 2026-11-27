package com.comagents.commons.domain.agent_context;

import org.jspecify.annotations.Nullable;

/**
 * An item an agent can't work without ({@code requiredItems} in its {@code agent.json}): any item of that type and
 * name. Without a type any item of that name matches, without a name any item of that type. Names ignore case.
 */
public record RequiredAgentContextItem(@Nullable AgentContextItemType type, @Nullable String name) {

    /**
     * @throws IllegalArgumentException when neither type nor name is given (it would match any item), or the name is
     *                                  blank (it would match none): a mistake in the agent's {@code agent.json}, not in
     *                                  a request
     */
    public RequiredAgentContextItem {
        if (name != null && name.isBlank()) {
            throw new IllegalArgumentException("A required item's name must not be blank");
        }
        if (type == null && name == null) {
            throw new IllegalArgumentException("A required item needs a type, a name, or both");
        }
        // like item names: surrounding whitespace is no part of the name
        name = name == null ? null : name.strip();
    }

    public boolean matches(AgentContextItem item) {
        return (type == null || type == item.type()) && (name == null || item.isNamed(name));
    }

    @SuppressWarnings("NullableProblems")
    @Override
    public String toString() {
        return (type == null ? "item" : type + " item") + (name == null ? "" : " " + name);
    }
}
