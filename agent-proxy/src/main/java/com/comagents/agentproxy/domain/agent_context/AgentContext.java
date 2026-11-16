package com.comagents.agentproxy.domain.agent_context;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.nio.charset.StandardCharsets;
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

    /**
     * Most bytes (UTF-8) of all item names, values and descriptions together. The context goes into the kick-off prompt, which
     * Claude Code gets as a single command-line argument, and Linux caps one argument at 128 KiB: half of it is left
     * for the context, the rest for the agent's own prompt and the list's wording.
     */
    public static final int MAX_BYTES = 64 * 1024;

    /** @throws InvalidAgentContextException when items repeat, are too large, or the repository to clone is ambiguous */
    public AgentContext {
        items = items == null ? List.of() : List.copyOf(items);
        requireUniqueItems(items);
        requireAtMostMaxBytes(items);
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

    private static void requireAtMostMaxBytes(List<AgentContextItem> items) {
        long bytes = items.stream()
                .mapToLong(item -> utf8Length(item.getName()) + utf8Length(item.getValue())
                        + item.getDescription().map(AgentContext::utf8Length).orElse(0))
                .sum();
        if (bytes > MAX_BYTES) {
            throw new InvalidAgentContextException("Agent context too large: " + bytes + " bytes of names, values and descriptions, "
                    + "at most " + MAX_BYTES);
        }
    }

    private static int utf8Length(String text) {
        return text.getBytes(StandardCharsets.UTF_8).length;
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
