package com.comagents.agentproxy.domain.agent_context;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentContextTest {

    private static final AgentContextItem NOTE = new AgentContextItem(AgentContextItemType.STRING, "note", "text");

    @Test
    void acceptsNoRepository() {
        assertDoesNotThrow(() -> new AgentContext(Set.of(NOTE)));
        assertDoesNotThrow(() -> new AgentContext(Set.of()));
    }

    @Test
    void acceptsOneRequiredRepositoryWithAnyOptionalOnes() {
        assertDoesNotThrow(() -> new AgentContext(Set.of(NOTE, repository("sources", true))));
        assertDoesNotThrow(() -> new AgentContext(Set.of(repository("sources", true), repository("docs", false),
                repository("examples", false))));
        assertDoesNotThrow(() -> new AgentContext(Set.of(repository("docs", false), repository("examples", false))));
    }

    @Test
    void rejectsSecondRequiredRepository() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new AgentContext(Set.of(repository("sources", true), repository("docs", true))));
        assertEquals("At most one required repository item is allowed, got 2", e.getMessage());
    }

    private static AgentContextItem repository(String name, boolean required) {
        return new AgentContextItem(new AgentContextItemAttributes(AgentContextItemType.REPOSITORY, name, required),
                "https://github.com/acme/" + name + ".git");
    }
}
