package com.comagents.agentproxy.domain.agent_context;

import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

/** A file in S3-compatible object storage: its file name, and its download URL as the value. */
@Serdeable
public final class FileAgentContextItem extends AgentContextItem {

    @Creator
    public FileAgentContextItem(String name, String value, @Nullable String description) {
        super(AgentContextItemType.FILE, name, value, description);
    }

    public FileAgentContextItem(String name, String value) {
        this(name, value, null);
    }
}
