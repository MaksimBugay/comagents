package com.comagents.agentproxy.domain.agent_context;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

        assertEquals("acme/kn-challenge", repository.getName());
        assertEquals("https://github.com/acme/kn-challenge.git", repository.getValue());
        assertFalse(repository.isPrimary());
    }

    @Test
    void repositoryNeedsGithubCloneUrl() {
        assertEquals("Not a GitHub repository URL: https://gitlab.com/acme/kn-challenge.git",
                assertThrows(InvalidAgentContextException.class,
                        () -> new GithubRepositoryAgentContextItem("https://gitlab.com/acme/kn-challenge.git")).getMessage());
        assertThrows(InvalidAgentContextException.class,
                () -> new GithubRepositoryAgentContextItem("https://github.com/acme/kn-challenge/tree/main"));
        assertEquals("acme/kn-challenge", new GithubRepositoryAgentContextItem("https://github.com/acme/kn-challenge").getName());
    }

    @Test
    void namesIgnoreCase() {
        assertEquals(new GithubRepositoryAgentContextItem("https://github.com/acme/kn-challenge.git"),
                new GithubRepositoryAgentContextItem("https://github.com/ACME/KN-Challenge.git"));
        assertEquals(new GithubRepositoryAgentContextItem("https://github.com/acme/kn-challenge.git").hashCode(),
                new GithubRepositoryAgentContextItem("https://github.com/ACME/KN-Challenge.git").hashCode());
        assertTrue(new GithubRepositoryAgentContextItem("https://github.com/ACME/KN-Challenge.git").isNamed("acme/kn-challenge"));
        assertEquals(new StringAgentContextItem("note", "a"), new StringAgentContextItem("Note", "b"));
        assertEquals(new FileAgentContextItem("a.md", "x").hashCode(), new FileAgentContextItem("A.MD", "y").hashCode());
        assertTrue(new StringAgentContextItem("Note", "a").isNamed("note"));
        assertFalse(new StringAgentContextItem("Note", "a").isNamed(null));
    }

    @Test
    void sameRepositoryNameUnderDifferentOwnersIsADifferentItem() {
        assertNotEquals(new GithubRepositoryAgentContextItem("https://github.com/acme/utils.git"),
                new GithubRepositoryAgentContextItem("https://github.com/other/utils.git"));
    }

    @Test
    void repositoryNeedsCloneUrlWithName() {
        assertThrows(InvalidAgentContextException.class, () -> new GithubRepositoryAgentContextItem(" "));
        assertThrows(InvalidAgentContextException.class, () -> new GithubRepositoryAgentContextItem("https://github.com/acme/.."));
    }
}
