package com.comagents.commons.domain.agent_context;

import com.fasterxml.jackson.annotation.JsonCreator;
import org.jspecify.annotations.Nullable;

/** A piece of text, e.g. {@code focus}: {@code security}. */
public final class StringAgentContextItem extends AgentContextItem {

    @JsonCreator
    public StringAgentContextItem(String name, String value, @Nullable String description) {
        super(AgentContextItemType.STRING, name, value, description);
    }

    public StringAgentContextItem(String name, String value) {
        this(name, value, null);
    }
}
