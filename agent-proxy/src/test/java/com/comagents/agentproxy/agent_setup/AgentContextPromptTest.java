package com.comagents.agentproxy.agent_setup;

import com.comagents.agentproxy.domain.agent_context.AgentContext;
import com.comagents.agentproxy.domain.agent_context.FileAgentContextItem;
import com.comagents.agentproxy.domain.agent_context.GithubRepositoryAgentContextItem;
import com.comagents.agentproxy.domain.agent_context.StringAgentContextItem;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AgentContextPromptTest {

    @Test
    void listsEveryItemButTheClonedRepository() {
        AgentContext context = new AgentContext(List.of(
                new StringAgentContextItem("focus", "security"),
                new FileAgentContextItem("requirements.md", "https://storage.example/requirements.md"),
                new GithubRepositoryAgentContextItem("https://github.com/acme/docs.git"),
                new GithubRepositoryAgentContextItem("https://github.com/acme/sources.git", true)));

        assertEquals(Optional.of("""
                Context of this session, besides the working directory. These items are not available locally; use one \
                only if it helps:
                - text "focus": security
                - file "requirements.md" (not downloaded), download URL: https://storage.example/requirements.md
                - repository "acme/docs" (not cloned): https://github.com/acme/docs.git"""),
                AgentContextPrompt.render(context));
    }

    @Test
    void theOnlyRepositoryIsClonedSoNotListed() {
        AgentContext onlyRepository = new AgentContext(List.of(new GithubRepositoryAgentContextItem("https://github.com/acme/docs.git")));
        AgentContext withText = new AgentContext(List.of(new GithubRepositoryAgentContextItem("https://github.com/acme/docs.git"),
                new StringAgentContextItem("focus", "security")));

        assertEquals(Optional.empty(), AgentContextPrompt.render(onlyRepository));
        assertFalse(AgentContextPrompt.render(withText).orElseThrow().contains("acme/docs"));
    }

    @Test
    void noContextNoSection() {
        assertEquals(Optional.empty(), AgentContextPrompt.render(null));
        assertEquals(Optional.empty(), AgentContextPrompt.render(new AgentContext(List.of())));
        assertEquals(Optional.empty(), AgentContextPrompt.render(new AgentContext(null)));
    }
}
