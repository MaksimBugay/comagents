package com.comagents.agentproxy.domain.agent_context;

import io.micronaut.core.annotation.Creator;
import io.micronaut.serde.annotation.Serdeable;

/** A piece of text, e.g. {@code focus}: {@code security}. */
@Serdeable
public final class StringAgentContextItem extends AgentContextItem {

    @Creator
    public StringAgentContextItem(String name, String value) {
        super(AgentContextItemType.STRING, name, value);
    }
}
