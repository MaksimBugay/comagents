package com.comagents.agentproxy.domain.agent_context;

import com.fasterxml.jackson.annotation.JsonCreator;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;

/** A file in S3-compatible object storage: its file name, and its download URL as the value. */
@Introspected
public final class FileAgentContextItem extends AgentContextItem {

    @JsonCreator
    public FileAgentContextItem(String name, String value, @Nullable String description) {
        super(AgentContextItemType.FILE, name, value, description);
    }

    public FileAgentContextItem(String name, String value) {
        this(name, value, null);
    }
}
