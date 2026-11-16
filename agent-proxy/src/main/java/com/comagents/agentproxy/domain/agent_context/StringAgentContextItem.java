package com.comagents.agentproxy.domain.agent_context;

import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

/** A piece of text, e.g. {@code focus}: {@code security}. */
@Serdeable
public final class StringAgentContextItem extends AgentContextItem {

    @Creator
    public StringAgentContextItem(String name, String value, @Nullable String description) {
        super(AgentContextItemType.STRING, name, value, description);
    }

    public StringAgentContextItem(String name, String value) {
        this(name, value, null);
    }
}
