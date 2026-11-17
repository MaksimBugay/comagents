package com.comagents.agentproxy.domain.agent_context;

import com.comagents.commons.serialization.JsonUtility;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** The type names live twice, in {@link AgentContextItemType} and in {@link AgentContextItem}'s {@code @JsonSubTypes}. */
class AgentContextItemTypeTest {

    @Test
    void everyTypeHasASubtypeOfTheSameName() {
        Set<String> subtypeNames = Arrays.stream(AgentContextItem.class.getAnnotation(JsonSubTypes.class).value())
                .map(JsonSubTypes.Type::name)
                .collect(Collectors.toSet());
        Set<String> typeNames = Arrays.stream(AgentContextItemType.values()).map(Enum::name).collect(Collectors.toSet());

        assertEquals(typeNames, subtypeNames);
    }

    @Test
    void theSubtypeOfEachTypeNameReportsThatType() {
        for (AgentContextItemType type : AgentContextItemType.values()) {
            AgentContextItem item = JsonUtility.fromJson(
                    "{\"type\": \"" + type.name() + "\", \"name\": \"sample\", \"value\": \"" + sampleValue(type) + "\"}",
                    AgentContextItem.class);

            assertNotNull(item, type.name());
            assertEquals(type, item.type());
        }
    }

    // exhaustive: a new type does not compile here until it gets a sample value
    private static String sampleValue(AgentContextItemType type) {
        return switch (type) {
            case STRING -> "text";
            case FILE -> "https://storage.example/sample.md";
            case GITHUB_REPOSITORY -> "https://github.com/acme/sample.git";
        };
    }
}
