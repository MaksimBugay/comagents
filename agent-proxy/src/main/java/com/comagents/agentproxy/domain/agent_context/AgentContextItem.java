package com.comagents.agentproxy.domain.agent_context;

import io.micronaut.core.annotation.Creator;
import io.micronaut.serde.annotation.Serdeable;
import org.apache.commons.lang3.StringUtils;

import java.util.Objects;

/**
 * Items are equal by type and name; whether they are required does not count.
 *
 * @param value the text of a {@code STRING} item, the clone URL of a {@code REPOSITORY}, the download URL (S3-compatible
 *              object storage) of a {@code FILE} named by its file name; must not be blank when the item is required
 */
@Serdeable
public record AgentContextItem(AgentContextItemAttributes attributes, String value) {

    @Creator
    public AgentContextItem {
        Objects.requireNonNull(attributes, "attributes");
        if (attributes.required() && StringUtils.isBlank(value)) {
            throw new IllegalArgumentException("Required item " + attributes.name() + " has no value");
        }
    }

    public AgentContextItem(AgentContextItemType type, String name, String value, boolean required) {
        this(new AgentContextItemAttributes(type, name, required), value);
    }

    public AgentContextItem(AgentContextItemType type, String name, String value) {
        this(type, name, value, false);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        AgentContextItem that = (AgentContextItem) o;
        return Objects.equals(attributes.name(), that.attributes.name()) && attributes.type() == that.attributes.type();
    }

    @Override
    public int hashCode() {
        return Objects.hash(attributes.type(), attributes.name());
    }
}
