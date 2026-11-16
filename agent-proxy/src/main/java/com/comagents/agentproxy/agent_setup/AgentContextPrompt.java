package com.comagents.agentproxy.agent_setup;

import com.comagents.agentproxy.domain.agent_context.AgentContext;
import com.comagents.agentproxy.domain.agent_context.AgentContextItem;
import com.comagents.agentproxy.domain.agent_context.GithubRepositoryAgentContextItem;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * A session's {@link AgentContext} as a section of the kick-off prompt. Only the cloned repository is local (the
 * working directory); other repositories and files are listed by URL, for the agent to fetch only if useful.
 */
public final class AgentContextPrompt {

    private static final Comparator<AgentContextItem> ORDER = Comparator
            .comparing(AgentContextItem::type)
            .thenComparing(AgentContextItem::getName);

    private AgentContextPrompt() {
    }

    /** The prompt section, or empty without a context or items. */
    public static Optional<String> render(AgentContext context) {
        if (context == null || context.items().isEmpty()) {
            return Optional.empty();
        }
        Optional<GithubRepositoryAgentContextItem> cloned = context.repositoryToClone();
        List<String> lines = context.items().stream()
                .sorted(ORDER)
                .map(item -> line(item, cloned.filter(item::equals).isPresent()))
                .toList();
        return Optional.of("Context of this session. Only the working directory is available locally; the other items "
                + "are listed for reference, use one only if it helps:\n" + String.join("\n", lines));
    }

    private static String line(AgentContextItem item, boolean cloned) {
        return switch (item.type()) {
            case GITHUB_REPOSITORY -> cloned
                    ? "- repository \"" + item.getName() + "\", cloned into the working directory: " + item.getValue()
                    : "- repository \"" + item.getName() + "\" (not cloned): " + item.getValue();
            case FILE -> "- file \"" + item.getName() + "\" (not downloaded), download URL: " + item.getValue();
            case STRING -> "- text \"" + item.getName() + "\": " + item.getValue();
        };
    }
}
