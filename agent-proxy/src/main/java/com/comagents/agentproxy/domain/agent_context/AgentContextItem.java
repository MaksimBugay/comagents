package com.comagents.agentproxy.domain.agent_context;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.micronaut.serde.annotation.Serdeable;
import org.apache.commons.lang3.StringUtils;

import java.util.Locale;
import java.util.Objects;

/**
 * One item of an {@link AgentContext}, in JSON {@code {"type": "STRING" | "FILE" | "GITHUB_REPOSITORY", ...}}. Items are
 * unique by type and name, names ignoring case; every item has a name and a value.
 */
@Serdeable
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = StringAgentContextItem.class, name = "STRING"),
        @JsonSubTypes.Type(value = FileAgentContextItem.class, name = "FILE"),
        @JsonSubTypes.Type(value = GithubRepositoryAgentContextItem.class, name = "GITHUB_REPOSITORY")})
public abstract sealed class AgentContextItem permits StringAgentContextItem, FileAgentContextItem, GithubRepositoryAgentContextItem {

    private final AgentContextItemType type;
    private final String name;
    private final String value;

    /**
     * @param type the subclass's own type, passed in rather than asked for, so construction never calls an overridable
     *             method
     * @throws InvalidAgentContextException when the name or the value is blank
     */
    protected AgentContextItem(AgentContextItemType type, String name, String value) {
        this.type = Objects.requireNonNull(type, "type");
        // surrounding whitespace is no part of a name: " focus" is "focus"
        this.name = requireNotBlank(type, name, "name", name).strip();
        this.value = requireNotBlank(type, value, "value", name);
    }

    public final AgentContextItemType type() {
        return type;
    }

    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }

    private static String requireNotBlank(AgentContextItemType type, String text, String what, String itemName) {
        if (StringUtils.isBlank(text)) {
            throw new InvalidAgentContextException(type + " item " + (itemName == null ? "" : itemName + " ")
                    + "has no " + what);
        }
        return text;
    }

    /**
     * Whether this item is called {@code otherName}; names ignore case.
     */
    public boolean isNamed(String otherName) {
        return otherName != null && nameKey(name).equals(nameKey(otherName));
    }

    private static String nameKey(String itemName) {
        return itemName.toLowerCase(Locale.ROOT);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof AgentContextItem that && type() == that.type() && isNamed(that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type(), nameKey(name));
    }

    @Override
    public String toString() {
        return type() + " " + name;
    }
}
