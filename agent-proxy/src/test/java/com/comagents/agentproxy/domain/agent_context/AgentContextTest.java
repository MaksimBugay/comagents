package com.comagents.agentproxy.domain.agent_context;

import com.comagents.commons.serialization.JsonUtility;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentContextTest {

    private static final StringAgentContextItem NOTE = new StringAgentContextItem("note", "text");
    private static final GithubRepositoryAgentContextItem SOURCES = new GithubRepositoryAgentContextItem("https://github.com/acme/sources.git", true);
    private static final GithubRepositoryAgentContextItem DOCS = new GithubRepositoryAgentContextItem("https://github.com/acme/docs.git");

    @Test
    void theOnlyRepositoryIsClonedEvenIfNotPrimary() {
        assertEquals(Optional.of(DOCS), new AgentContext(List.of(NOTE, DOCS)).repositoryToClone());
    }

    @Test
    void ofSeveralRepositoriesThePrimaryIsCloned() {
        assertEquals(Optional.of(SOURCES), new AgentContext(List.of(NOTE, DOCS, SOURCES)).repositoryToClone());
    }

    @Test
    void severalRepositoriesNeedOnePrimary() {
        InvalidAgentContextException none = assertThrows(InvalidAgentContextException.class,
                () -> new AgentContext(List.of(DOCS, new GithubRepositoryAgentContextItem("https://github.com/acme/examples.git"))));
        assertEquals("2 repositories but none is primary: mark the one to clone as primary", none.getMessage());

        InvalidAgentContextException two = assertThrows(InvalidAgentContextException.class,
                () -> new AgentContext(List.of(SOURCES, new GithubRepositoryAgentContextItem("https://github.com/acme/docs.git", true))));
        assertEquals("At most one primary repository is allowed, got 2", two.getMessage());
    }

    @Test
    void noRepositoryNothingToClone() {
        assertEquals(Optional.empty(), new AgentContext(List.of(NOTE)).repositoryToClone());
        assertEquals(Optional.empty(), new AgentContext(null).repositoryToClone());
    }

    @Test
    void itemsAreUniqueByTypeAndName() {
        InvalidAgentContextException e = assertThrows(InvalidAgentContextException.class,
                () -> new AgentContext(List.of(NOTE, new StringAgentContextItem("note", "other text"))));
        assertEquals("Duplicate item STRING note", e.getMessage());
        assertDoesNotThrow(() -> new AgentContext(List.of(NOTE, new FileAgentContextItem("note", "https://storage.example/note"))));
    }

    @Test
    void sizeIsNotLimited() {
        // an agent's response is a context too; only a start request's context is limited (AgentContextValidator)
        assertDoesNotThrow(() -> new AgentContext(List.of(new StringAgentContextItem("a", "x".repeat(AgentContextValidator.MAX_BYTES + 1)))));
    }

    @Test
    void readsTypedItemsFromJson() {
        AgentContext context = JsonUtility.fromJson("""
                {"items": [
                  {"type": "GITHUB_REPOSITORY", "value": "https://github.com/acme/kn-challenge.git", "primary": true},
                  {"type": "FILE", "name": "requirements.md", "value": "https://storage.example/requirements.md",
                   "description": "acceptance criteria"},
                  {"type": "STRING", "name": "focus", "value": "security"}]}""", AgentContext.class);

        assertNotNull(context);
        GithubRepositoryAgentContextItem repository = assertInstanceOf(GithubRepositoryAgentContextItem.class, context.items().getFirst());
        assertEquals("acme/kn-challenge", repository.getName());
        assertTrue(repository.isPrimary());
        FileAgentContextItem file = assertInstanceOf(FileAgentContextItem.class, context.items().get(1));
        assertEquals(Optional.of("acceptance criteria"), file.getDescription());
        assertEquals(Optional.empty(), repository.getDescription());
        assertEquals("security", assertInstanceOf(StringAgentContextItem.class, context.items().get(2)).getValue());
    }

    @Test
    void jsonRoundTrip() {
        AgentContext context = new AgentContext(List.of(NOTE, SOURCES, new FileAgentContextItem("a.md", "https://storage.example/a.md")));

        String json = JsonUtility.toJson(context);

        assertEquals(context, JsonUtility.fromJson(json, AgentContext.class), json);
    }
}
