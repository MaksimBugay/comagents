package com.comagents.agentproxy.domain.agent_context;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentContextValidatorTest {

    private static final AgentContextItem NOTE = new AgentContextItem(AgentContextItemType.STRING, "note", "text");
    private static final AgentContextItem SOURCES = repository("sources", true);
    private static final AgentContextItem DOCS = repository("docs", false);

    private final AgentContextValidator validator = new AgentContextValidator();

    @Test
    void acceptsAnythingWhenNothingIsRequired() {
        assertDoesNotThrow(() -> validator.validate(Set.of(NOTE, SOURCES, DOCS), null));
        assertDoesNotThrow(() -> validator.validate(null, Set.of()));
    }

    @Test
    void requiredItemsMustBePresentWithValue() {
        assertDoesNotThrow(() -> validator.validate(Set.of(NOTE, SOURCES), Set.of(NOTE.attributes(), SOURCES.attributes())));

        InvalidAgentContextException missing = assertThrows(InvalidAgentContextException.class,
                () -> validator.validate(Set.of(NOTE, DOCS), Set.of(SOURCES.attributes())));
        assertEquals("Required item REPOSITORY sources is missing or has no value", missing.getMessage());
        assertThrows(InvalidAgentContextException.class, () -> validator.validate(null, Set.of(SOURCES.attributes())));
        AgentContextItem blank = new AgentContextItem(AgentContextItemType.STRING, "note", " ");
        assertThrows(InvalidAgentContextException.class, () -> validator.validate(Set.of(blank), Set.of(blank.attributes())));
    }

    @Test
    void requiredPrimaryRepositoryMustBePrimary() {
        assertThrows(InvalidAgentContextException.class,
                () -> validator.validate(Set.of(repository("sources", false)), Set.of(SOURCES.attributes())));
    }

    @Test
    void atMostOnePrimaryRepository() {
        assertDoesNotThrow(() -> validator.validate(Set.of(SOURCES, DOCS, repository("examples", false)), null));

        InvalidAgentContextException e = assertThrows(InvalidAgentContextException.class,
                () -> validator.validate(Set.of(SOURCES, repository("docs", true)), null));
        assertEquals("At most one primary repository is allowed, got 2", e.getMessage());
    }

    private static AgentContextItem repository(String name, boolean primary) {
        return new AgentContextItem(new AgentContextItemAttributes(AgentContextItemType.REPOSITORY, name, primary),
                "https://github.com/acme/" + name + ".git");
    }
}
