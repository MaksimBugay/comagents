package com.comagents.agentproxy.agent_setup;

import com.comagents.agentproxy.domain.agent_context.AgentContext;
import com.comagents.agentproxy.domain.agent_context.FileAgentContextItem;
import com.comagents.agentproxy.domain.agent_context.GithubRepositoryAgentContextItem;
import com.comagents.agentproxy.domain.agent_context.StringAgentContextItem;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentContextPromptTest {

    @Test
    void listsEveryItem() {
        AgentContext context = new AgentContext(List.of(
                new StringAgentContextItem("focus", "security"),
                new FileAgentContextItem("requirements.md", "https://storage.example/requirements.md"),
                new GithubRepositoryAgentContextItem("https://github.com/acme/docs.git"),
                new GithubRepositoryAgentContextItem("https://github.com/acme/sources.git", true)));

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
    void theOnlyRepositoryIsTheClonedOne() {
        AgentContext context = new AgentContext(List.of(new GithubRepositoryAgentContextItem("https://github.com/acme/docs.git")));

        assertTrue(AgentContextPrompt.render(context).orElseThrow()
                .endsWith("- repository \"docs\", cloned into the working directory: https://github.com/acme/docs.git"));
    }

    @Test
    void noContextNoSection() {
        assertEquals(Optional.empty(), AgentContextPrompt.render(null));
        assertEquals(Optional.empty(), AgentContextPrompt.render(new AgentContext(List.of())));
        assertEquals(Optional.empty(), AgentContextPrompt.render(new AgentContext(null)));
    }
}
