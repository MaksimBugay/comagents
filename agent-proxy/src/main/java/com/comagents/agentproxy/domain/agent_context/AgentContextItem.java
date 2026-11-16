package com.comagents.agentproxy.domain.agent_context;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.micronaut.serde.annotation.Serdeable;
import org.apache.commons.lang3.StringUtils;

import java.util.Objects;

/**
 * One item of an {@link AgentContext}, in JSON {@code {"type": "STRING" | "FILE" | "GITHUB_REPOSITORY", ...}}. Items are
 * unique by type and name; every item has a name and a value.
 */
@Serdeable
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = StringAgentContextItem.class, name = "STRING"),
        @JsonSubTypes.Type(value = FileAgentContextItem.class, name = "FILE"),
        @JsonSubTypes.Type(value = GithubRepositoryAgentContextItem.class, name = "GITHUB_REPOSITORY")})
public abstract sealed class AgentContextItem permits StringAgentContextItem, FileAgentContextItem, GithubRepositoryAgentContextItem {

    private final String name;
    private final String value;

    /** @throws InvalidAgentContextException when the name or the value is blank */
    protected AgentContextItem(String name, String value) {
        this.name = requireNotBlank(name, "name", name);
        this.value = requireNotBlank(value, "value", name);
    }

    public abstract AgentContextItemType type();

    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }

    private String requireNotBlank(String text, String what, String itemName) {
        if (StringUtils.isBlank(text)) {
            throw new InvalidAgentContextException(type() + " item " + (itemName == null ? "" : itemName + " ")
                    + "has no " + what);
        }
        return text;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof AgentContextItem that && type() == that.type() && name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type(), name);
    }

    @Override
    public String toString() {
        return type() + " " + name;
    }
}
