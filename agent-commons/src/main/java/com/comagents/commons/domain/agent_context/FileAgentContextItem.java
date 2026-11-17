package com.comagents.commons.domain.agent_context;

import com.fasterxml.jackson.annotation.JsonCreator;
import org.jspecify.annotations.Nullable;

/** A file in S3-compatible object storage: its file name, and its download URL as the value. */
public final class FileAgentContextItem extends AgentContextItem {

    @JsonCreator
    public FileAgentContextItem(String name, String value, @Nullable String description) {
        super(AgentContextItemType.FILE, name, value, description);
    }

    public FileAgentContextItem(String name, String value) {
        this(name, value, null);
    }
}
