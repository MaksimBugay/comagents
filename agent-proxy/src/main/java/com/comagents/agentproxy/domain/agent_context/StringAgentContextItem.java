package com.comagents.agentproxy.domain.agent_context;

import com.fasterxml.jackson.annotation.JsonCreator;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;

/** A piece of text, e.g. {@code focus}: {@code security}. */
@Introspected
public final class StringAgentContextItem extends AgentContextItem {

    @JsonCreator
    public StringAgentContextItem(String name, String value, @Nullable String description) {
        super(AgentContextItemType.STRING, name, value, description);
    }

    public StringAgentContextItem(String name, String value) {
        this(name, value, null);
    }
}
