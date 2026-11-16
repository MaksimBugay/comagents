package com.comagents.agentproxy.domain.agent_context;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentContextItemTest {

    @Test
    void itemsAreEqualByTypeAndName() {
        assertEquals(new AgentContextItem(AgentContextItemType.STRING, "note", "a"),
                new AgentContextItem(AgentContextItemType.STRING, "note", "b"));
        assertNotEquals(new AgentContextItem(AgentContextItemType.STRING, "note", "a"),
                new AgentContextItem(AgentContextItemType.FILE, "note", "a"));
    }

    @Test
    void needsAttributes() {
        assertThrows(NullPointerException.class, () -> new AgentContextItem(null, "a"));
    }
}
