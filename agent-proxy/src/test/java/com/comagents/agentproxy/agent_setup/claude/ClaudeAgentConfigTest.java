package com.comagents.agentproxy.agent_setup.claude;

import com.comagents.agentproxy.domain.agent_context.AgentContextItemAttributes;
import com.comagents.agentproxy.domain.agent_context.AgentContextItemType;
import io.micronaut.serde.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClaudeAgentConfigTest {

    @Test
    void readsRequiredItems() throws IOException {
        ClaudeAgentConfig config = read("""
                {"kickOffPrompt": "Review it",
                 "requiredItems": [{"type": "REPOSITORY", "name": "sources", "primary": true}]}""");

        assertEquals(Set.of(new AgentContextItemAttributes(AgentContextItemType.REPOSITORY, "sources", true)),
                config.requiredItems());
    }

    @Test
    void noRequiredItemsByDefault() throws IOException {
        assertEquals(Set.of(), read("""
                {"kickOffPrompt": "Review it"}""").requiredItems());
    }

    private static ClaudeAgentConfig read(String json) throws IOException {
        return Objects.requireNonNull(ObjectMapper.getDefault().readValue(json, ClaudeAgentConfig.class));
    }
}
