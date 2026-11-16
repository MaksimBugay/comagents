package com.comagents.agentproxy.agent_setup.claude;

import com.comagents.agentproxy.domain.agent_context.RequiredAgentContextItem;
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
                 "requiredItems": [{"type": "GITHUB_REPOSITORY", "name": "sources"}, {"type": "FILE"}]}""");

        assertEquals(Set.of(new RequiredAgentContextItem(AgentContextItemType.GITHUB_REPOSITORY, "sources"),
                        new RequiredAgentContextItem(AgentContextItemType.FILE, null)),
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
