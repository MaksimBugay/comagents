package com.comagents.agentproxy.domain.agent_context;

import com.comagents.commons.domain.AiAgent;
import com.comagents.commons.domain.GithubRepositoryUrl;
import com.comagents.commons.domain.agent_context.AgentContext;
import com.comagents.commons.domain.agent_context.AgentContextItem;
import com.comagents.commons.domain.agent_context.GithubRepositoryAgentContextItem;
import com.comagents.commons.domain.agent_context.InvalidAgentContextException;
import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Singleton;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

/**
 * Checks a start request's agent context against the agent: it must fit into the kick-off prompt
 * ({@link #MAX_BYTES}), every item the agent requires ({@code requiredItems} in its {@code agent.json}) must be in it,
 * and the repository to clone must not land in the agent's own folder. Everything else about a context is checked by
 * {@link AgentContext} itself.
 */
@Singleton
public class AgentContextValidator {

    /**
     * Most bytes (UTF-8) of all item names, values and descriptions together. The context goes into the kick-off prompt, which
     * Claude Code gets as a single command-line argument, and Linux caps one argument at 128 KiB: half of it is left
     * for the context, the rest for the agent's own prompt and the list's wording.
     */
    public static final int MAX_BYTES = 64 * 1024;

    /**
     * @param agent         the agent the session runs
     * @param context       the request's context; none when null
     * @param requiredItems the items the agent can't work without
     * @throws InvalidAgentContextException when the context is too large, a required item is missing, or the
     *                                      repository to clone has the agent's name
     */
    public void validate(AiAgent agent, @Nullable AgentContext context, Set<RequiredAgentContextItem> requiredItems) {
        List<AgentContextItem> items = context == null ? List.of() : context.items();
        requireAtMostMaxBytes(items);
        for (RequiredAgentContextItem required : requiredItems) {
            if (items.stream().noneMatch(required::matches)) {
                throw new InvalidAgentContextException("Required " + required + " is missing");
            }
        }
        if (context != null) {
            context.repositoryToClone().ifPresent(repository -> requireOwnFolder(agent, repository));
        }
    }

    private static void requireAtMostMaxBytes(List<AgentContextItem> items) {
        long bytes = items.stream()
                .mapToLong(item -> utf8Length(item.getName()) + utf8Length(item.getValue())
                        + item.getDescription().map(AgentContextValidator::utf8Length).orElse(0))
                .sum();
        if (bytes > MAX_BYTES) {
            throw new InvalidAgentContextException("Agent context too large: " + bytes + " bytes of names, values and descriptions, "
                    + "at most " + MAX_BYTES);
        }
    }

    private static int utf8Length(String text) {
        return text.getBytes(StandardCharsets.UTF_8).length;
    }

    // the repository is cloned into <session folder>/<repository>, the agent's files go to <session folder>/<agent
    // name>: with the same name (ignoring case, as some file systems do) the agent's files would overwrite the clone's
    private static void requireOwnFolder(AiAgent agent, GithubRepositoryAgentContextItem repository) {
        String folder = GithubRepositoryUrl.parse(repository.getValue()).repository();
        if (folder.equalsIgnoreCase(agent.getAgentName())) {
            throw new InvalidAgentContextException("Repository " + repository.getName() + " can't be cloned for agent "
                    + agent.getAgentName() + ": its folder " + folder + " is the agent's own folder");
        }
    }
}
