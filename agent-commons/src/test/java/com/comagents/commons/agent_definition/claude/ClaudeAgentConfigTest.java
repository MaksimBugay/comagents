package com.comagents.commons.agent_definition.claude;

import com.comagents.commons.domain.agent_context.RequiredAgentContextItem;
import com.comagents.commons.domain.agent_context.AgentContextItemType;
import com.comagents.commons.serialization.JsonUtility;
import com.comagents.commons.serialization.SerializationException;
import org.junit.jupiter.api.Test;

import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaudeAgentConfigTest {

    @Test
    void readsRequiredItems() {
        ClaudeAgentConfig config = read("""
                {"kickOffPrompt": "Review it",
                 "requiredItems": [{"type": "GITHUB_REPOSITORY", "name": "acme/sources"}, {"type": "FILE"}]}""");

        assertEquals(Set.of(new RequiredAgentContextItem(AgentContextItemType.GITHUB_REPOSITORY, "acme/sources"),
                        new RequiredAgentContextItem(AgentContextItemType.FILE, null)),
                config.requiredItems());
    }

    @Test
    void noRequiredItemsByDefault() {
        assertEquals(Set.of(), read("""
                {"kickOffPrompt": "Review it"}""").requiredItems());
    }

    @Test
    void readsReadOnlyAndStateless() {
        ClaudeAgentConfig config = read("""
                {"kickOffPrompt": "Forecast it", "readOnly": true, "stateless": true}""");

        assertTrue(config.readOnly());
        assertTrue(config.stateless());
    }

    @Test
    void neitherReadOnlyNorStatelessByDefault() {
        ClaudeAgentConfig config = read("""
                {"kickOffPrompt": "Review it"}""");

        assertFalse(config.readOnly());
        assertFalse(config.stateless());
    }

    @Test
    void requiredItemNeedsTypeOrName() {
        assertThrows(SerializationException.class, () -> read("""
                {"kickOffPrompt": "Review it", "requiredItems": [{}]}"""), "would match any item");
        assertThrows(SerializationException.class, () -> read("""
                {"kickOffPrompt": "Review it", "requiredItems": [{"type": "FILE", "name": " "}]}"""), "would match none");
        assertEquals("A required item needs a type, a name, or both",
                assertThrows(IllegalArgumentException.class, () -> new RequiredAgentContextItem(null, null)).getMessage());
    }

    private static ClaudeAgentConfig read(String json) {
        return Objects.requireNonNull(JsonUtility.fromJson(json, ClaudeAgentConfig.class));
    }
}
