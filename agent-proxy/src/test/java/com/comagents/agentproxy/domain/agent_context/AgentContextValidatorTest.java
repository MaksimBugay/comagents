package com.comagents.agentproxy.domain.agent_context;

import com.comagents.agentproxy.domain.AiAgent;
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
        assertDoesNotThrow(() -> validator.validate(AiAgent.CODE_REVIEW, CONTEXT, Set.of()));
        assertDoesNotThrow(() -> validator.validate(AiAgent.CODE_REVIEW, null, Set.of()));
    }

    @Test
    void requiredItemMatchesByTypeAndName() {
        assertDoesNotThrow(() -> validator.validate(AiAgent.CODE_REVIEW, CONTEXT, Set.of(required(AgentContextItemType.GITHUB_REPOSITORY, "acme/kn-challenge"))));
        assertDoesNotThrow(() -> validator.validate(AiAgent.CODE_REVIEW, CONTEXT, Set.of(required(AgentContextItemType.GITHUB_REPOSITORY, "ACME/KN-Challenge"),
                required(AgentContextItemType.STRING, "Focus"))), "names ignore case");

        InvalidAgentContextException e = assertThrows(InvalidAgentContextException.class,
                () -> validator.validate(AiAgent.CODE_REVIEW, CONTEXT, Set.of(required(AgentContextItemType.GITHUB_REPOSITORY, "other/kn-challenge"))));
        assertEquals("Required GITHUB_REPOSITORY item other/kn-challenge is missing", e.getMessage());
        assertThrows(InvalidAgentContextException.class,
                () -> validator.validate(AiAgent.CODE_REVIEW, CONTEXT, Set.of(required(AgentContextItemType.FILE, "focus"))));
    }

    @Test
    void omittedTypeOrNameMatchesAny() {
        assertDoesNotThrow(() -> validator.validate(AiAgent.CODE_REVIEW, CONTEXT, Set.of(required(AgentContextItemType.GITHUB_REPOSITORY, null))));
        assertDoesNotThrow(() -> validator.validate(AiAgent.CODE_REVIEW, CONTEXT, Set.of(required(null, "focus"))));

        assertEquals("Required FILE item is missing", assertThrows(InvalidAgentContextException.class,
                () -> validator.validate(AiAgent.CODE_REVIEW, CONTEXT, Set.of(required(AgentContextItemType.FILE, null)))).getMessage());
        assertThrows(InvalidAgentContextException.class,
                () -> validator.validate(AiAgent.CODE_REVIEW, null, Set.of(required(AgentContextItemType.GITHUB_REPOSITORY, null))));
    }

    @Test
    void repositoryToCloneMustNotBeNamedLikeTheAgent() {
        AgentContext sameName = new AgentContext(List.of(new GithubRepositoryAgentContextItem("https://github.com/acme/Code_Review.git")));

        InvalidAgentContextException e = assertThrows(InvalidAgentContextException.class,
                () -> validator.validate(AiAgent.CODE_REVIEW, sameName, Set.of()));
        assertEquals("Repository acme/Code_Review can't be cloned for agent code_review: its folder Code_Review is the "
                + "agent's own folder", e.getMessage());
        // only the cloned repository lands in the session folder
        assertDoesNotThrow(() -> validator.validate(AiAgent.CODE_REVIEW, new AgentContext(List.of(
                new GithubRepositoryAgentContextItem("https://github.com/acme/kn-challenge.git", true),
                new GithubRepositoryAgentContextItem("https://github.com/acme/code_review.git"))), Set.of()));
    }

    @Test
    void namesAndValuesTogetherAreLimited() {
        String half = "x".repeat(AgentContextValidator.MAX_BYTES / 2 - 4);
        assertDoesNotThrow(() -> validator.validate(AiAgent.CODE_REVIEW, new AgentContext(List.of(
                new StringAgentContextItem("a", half), new StringAgentContextItem("b", half))), Set.of()));

        InvalidAgentContextException e = assertThrows(InvalidAgentContextException.class, () -> validator.validate(AiAgent.CODE_REVIEW,
                new AgentContext(List.of(new StringAgentContextItem("a", half), new StringAgentContextItem("b", half + "xxxxxxxx"))), Set.of()));
        assertEquals("Agent context too large: 65538 bytes of names, values and descriptions, at most 65536", e.getMessage());
        // counted in UTF-8 bytes: "é" takes two
        assertThrows(InvalidAgentContextException.class, () -> validator.validate(AiAgent.CODE_REVIEW,
                new AgentContext(List.of(new StringAgentContextItem("a", "é".repeat(AgentContextValidator.MAX_BYTES / 2 + 1)))), Set.of()));
    }

    private static RequiredAgentContextItem required(AgentContextItemType type, String name) {
        return new RequiredAgentContextItem(type, name);
    }
}
