package com.comagents.agentproxy.domain.agent_context;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * What an agent session gets to work with. Items are unique by type and name. Of its repositories one is cloned as
 * the session's working directory: the only one, or the primary one when there are several.
 *
 * @param items the context's items; empty when null
 */
@Serdeable
public record AgentContext(@Nullable List<AgentContextItem> items) {

    /** @throws InvalidAgentContextException when items repeat, or the repository to clone is ambiguous */
    public AgentContext {
        items = items == null ? List.of() : List.copyOf(items);
        requireUniqueItems(items);
        requireUnambiguousRepositoryToClone(repositories(items));
    }

    /** The repository cloned as the session's working directory: the only one, or the primary one. */
    public Optional<GithubRepositoryAgentContextItem> repositoryToClone() {
        List<GithubRepositoryAgentContextItem> repositories = repositories(items);
        if (repositories.size() == 1) {
            return Optional.of(repositories.getFirst());
        }
        return repositories.stream().filter(GithubRepositoryAgentContextItem::isPrimary).findFirst();
    }

    private static List<GithubRepositoryAgentContextItem> repositories(List<AgentContextItem> items) {
        return items.stream()
                .filter(GithubRepositoryAgentContextItem.class::isInstance)
                .map(GithubRepositoryAgentContextItem.class::cast)
                .toList();
    }

    private static void requireUniqueItems(List<AgentContextItem> items) {
        Set<AgentContextItem> seen = new HashSet<>();
        for (AgentContextItem item : items) {
            if (!seen.add(item)) {
                throw new InvalidAgentContextException("Duplicate item " + item);
            }
        }
    }

    private static void requireUnambiguousRepositoryToClone(List<GithubRepositoryAgentContextItem> repositories) {
        long primary = repositories.stream().filter(GithubRepositoryAgentContextItem::isPrimary).count();
        if (primary > 1) {
            throw new InvalidAgentContextException("At most one primary repository is allowed, got " + primary);
        }
        if (repositories.size() > 1 && primary == 0) {
            throw new InvalidAgentContextException(repositories.size() + " repositories but none is primary: mark the "
                    + "one to clone as primary");
        }
    }
}
