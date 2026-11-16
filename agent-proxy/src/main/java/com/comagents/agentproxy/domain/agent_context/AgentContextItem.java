package com.comagents.agentproxy.domain.agent_context;

import io.micronaut.core.annotation.Creator;
import io.micronaut.serde.annotation.Serdeable;

import java.util.Objects;

/**
 * Items are equal by their attributes, whatever their value.
 *
 * @param value the text of a {@code STRING} item, the clone URL of a {@code REPOSITORY}, the download URL (S3-compatible
 *              object storage) of a {@code FILE} named by its file name
 */
@Serdeable
public record AgentContextItem(AgentContextItemAttributes attributes, String value) {

    @Creator
    public AgentContextItem {
        Objects.requireNonNull(attributes, "attributes");
    }

    public AgentContextItem(AgentContextItemType type, String name, String value) {
        this(new AgentContextItemAttributes(type, name), value);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        return attributes.equals(((AgentContextItem) o).attributes);
    }

    @Override
    public int hashCode() {
        return attributes.hashCode();
    }

    public static boolean isPrimaryRepository(AgentContextItem item) {
        return item.attributes().type() == AgentContextItemType.REPOSITORY && item.attributes().primary();
    }
}
