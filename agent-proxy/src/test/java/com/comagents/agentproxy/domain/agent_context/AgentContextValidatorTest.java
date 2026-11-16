package com.comagents.agentproxy.domain.agent_context;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentContextValidatorTest {

    private static final AgentContext CONTEXT = new AgentContext(List.of(
            new StringAgentContextItem("focus", "security"),
            new GithubRepositoryAgentContextItem("https://github.com/acme/kn-challenge.git")));

    private final AgentContextValidator validator = new AgentContextValidator();

    @Test
    void acceptsAnythingWhenNothingIsRequired() {
        assertDoesNotThrow(() -> validator.validate(CONTEXT, Set.of()));
        assertDoesNotThrow(() -> validator.validate(null, Set.of()));
    }

    @Test
    void requiredItemMatchesByTypeAndName() {
        assertDoesNotThrow(() -> validator.validate(CONTEXT, Set.of(required(AgentContextItemType.GITHUB_REPOSITORY, "kn-challenge"))));

        InvalidAgentContextException e = assertThrows(InvalidAgentContextException.class,
                () -> validator.validate(CONTEXT, Set.of(required(AgentContextItemType.GITHUB_REPOSITORY, "other"))));
        assertEquals("Required GITHUB_REPOSITORY item other is missing", e.getMessage());
        assertThrows(InvalidAgentContextException.class,
                () -> validator.validate(CONTEXT, Set.of(required(AgentContextItemType.FILE, "focus"))));
    }

    @Test
    void omittedTypeOrNameMatchesAny() {
        assertDoesNotThrow(() -> validator.validate(CONTEXT, Set.of(required(AgentContextItemType.GITHUB_REPOSITORY, null))));
        assertDoesNotThrow(() -> validator.validate(CONTEXT, Set.of(required(null, "focus"))));

        assertEquals("Required FILE item is missing", assertThrows(InvalidAgentContextException.class,
                () -> validator.validate(CONTEXT, Set.of(required(AgentContextItemType.FILE, null)))).getMessage());
        assertThrows(InvalidAgentContextException.class,
                () -> validator.validate(null, Set.of(required(AgentContextItemType.GITHUB_REPOSITORY, null))));
    }

    private static RequiredAgentContextItem required(AgentContextItemType type, String name) {
        return new RequiredAgentContextItem(type, name);
    }
}
