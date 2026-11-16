package com.comagents.agentproxy.domain.agent_context;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentContextItemTest {

    @Test
    void itemsAreEqualByTypeAndName() {
        assertEquals(new StringAgentContextItem("note", "a"), new StringAgentContextItem("note", "b"));
        assertNotEquals(new StringAgentContextItem("note", "a"), new FileAgentContextItem("note", "a"));
    }

    @Test
    void everyItemNeedsNameAndValue() {
        assertEquals("STRING item note has no value",
                assertThrows(InvalidAgentContextException.class, () -> new StringAgentContextItem("note", " ")).getMessage());
        assertEquals("FILE item has no name",
                assertThrows(InvalidAgentContextException.class, () -> new FileAgentContextItem(null, "https://x")).getMessage());
    }

    @Test
    void repositoryIsNamedAfterItsCloneUrl() {
        GithubRepositoryAgentContextItem repository = new GithubRepositoryAgentContextItem("https://github.com/acme/kn-challenge.git");

        assertEquals("kn-challenge", repository.getName());
        assertEquals("https://github.com/acme/kn-challenge.git", repository.getValue());
        assertFalse(repository.isPrimary());
        assertEquals("kn-challenge", GithubRepositoryAgentContextItem.repositoryName("https://github.com/acme/kn-challenge/"));
    }

    @Test
    void repositoryNeedsGithubCloneUrl() {
        assertEquals("Not a GitHub repository URL: https://gitlab.com/acme/kn-challenge.git",
                assertThrows(InvalidAgentContextException.class,
                        () -> new GithubRepositoryAgentContextItem("https://gitlab.com/acme/kn-challenge.git")).getMessage());
        assertThrows(InvalidAgentContextException.class,
                () -> new GithubRepositoryAgentContextItem("https://github.com/acme/kn-challenge/tree/main"));
        assertEquals("kn-challenge", new GithubRepositoryAgentContextItem("https://github.com/acme/kn-challenge").getName());
    }

    @Test
    void repositoryNeedsCloneUrlWithName() {
        assertThrows(InvalidAgentContextException.class, () -> new GithubRepositoryAgentContextItem(" "));
        assertThrows(InvalidAgentContextException.class, () -> new GithubRepositoryAgentContextItem("https://github.com/acme/.."));
    }
}
