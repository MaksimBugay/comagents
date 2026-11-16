package com.comagents.agentproxy.agent_setup;

import com.comagents.agentproxy.domain.agent_context.AgentContext;
import com.comagents.agentproxy.domain.agent_context.AgentContextItem;
import com.comagents.agentproxy.domain.agent_context.AgentContextItemAttributes;
import com.comagents.agentproxy.domain.agent_context.AgentContextItemType;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentContextPromptTest {

    @Test
    void listsEveryItemWithAValue() {
        AgentContextItem sources = new AgentContextItem(
                new AgentContextItemAttributes(AgentContextItemType.REPOSITORY, "sources", true), "https://github.com/acme/sources.git");
        AgentContextItem focus = item(AgentContextItemType.STRING, "focus", "security");
        AgentContext context = new AgentContext(Set.of(
                focus,
                item(AgentContextItemType.FILE, "requirements.md", "https://storage.example/requirements.md"),
                item(AgentContextItemType.REPOSITORY, "docs", "https://github.com/acme/docs.git"),
                sources,
                item(AgentContextItemType.STRING, "empty", " ")));

        assertEquals(Optional.of("""
                Context of this session. Only the working directory is available locally; the other items are listed \
                for reference, use one only if it helps:
                - text "focus": security
                - file "requirements.md" (not downloaded), download URL: https://storage.example/requirements.md
                - repository "docs" (not cloned): https://github.com/acme/docs.git
                - repository "sources", cloned into the working directory: https://github.com/acme/sources.git"""),
                AgentContextPrompt.render(context));
    }

    @Test
    void noContextNoSection() {
        assertEquals(Optional.empty(), AgentContextPrompt.render(null));
        assertEquals(Optional.empty(), AgentContextPrompt.render(new AgentContext(Set.of())));
        assertEquals(Optional.empty(), AgentContextPrompt.render(new AgentContext(null)));
    }

    private static AgentContextItem item(AgentContextItemType type, String name, String value) {
        return new AgentContextItem(type, name, value);
    }
}
