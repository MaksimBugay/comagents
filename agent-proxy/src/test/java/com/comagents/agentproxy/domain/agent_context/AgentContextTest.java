package com.comagents.agentproxy.domain.agent_context;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentContextTest {

    @Test
    void primaryRepositoryIsTheOneToClone() {
        AgentContextItem note = new AgentContextItem(AgentContextItemType.STRING, "note", "text");
        AgentContextItem sources = new AgentContextItem(
                new AgentContextItemAttributes(AgentContextItemType.REPOSITORY, "sources", true), "https://github.com/acme/sources.git");
        AgentContextItem docs = new AgentContextItem(AgentContextItemType.REPOSITORY, "docs", "https://github.com/acme/docs.git");

        assertEquals(Optional.of(sources), new AgentContext(Set.of(note, sources, docs)).primaryRepository());
        assertEquals(Optional.empty(), new AgentContext(Set.of(note, docs)).primaryRepository());
        assertEquals(Optional.empty(), new AgentContext(null).primaryRepository());
    }
}
