package com.comagents.agentproxy.domain.agent_context;

import com.comagents.agentproxy.domain.AiAgent;
import com.comagents.agentproxy.domain.GithubRepositoryUrl;
import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Set;

/**
 * Checks a start request's agent context against the agent: every item the agent requires ({@code requiredItems} in
 * its {@code agent.json}) must be in it, and the repository to clone must not land in the agent's own folder.
 * Everything else about a context is checked by {@link AgentContext} itself.
 */
@Singleton
public class AgentContextValidator {

    /**
     * @param agent         the agent the session runs
     * @param context       the request's context; none when null
     * @param requiredItems the items the agent can't work without
     * @throws InvalidAgentContextException when a required item is missing, or the repository to clone has the
     *                                      agent's name
     */
    public void validate(AiAgent agent, @Nullable AgentContext context, Set<RequiredAgentContextItem> requiredItems) {
        List<AgentContextItem> items = context == null ? List.of() : context.items();
        for (RequiredAgentContextItem required : requiredItems) {
            if (items.stream().noneMatch(required::matches)) {
                throw new InvalidAgentContextException("Required " + required + " is missing");
            }
        }
        if (context != null) {
            context.repositoryToClone().ifPresent(repository -> requireOwnFolder(agent, repository));
        }
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
