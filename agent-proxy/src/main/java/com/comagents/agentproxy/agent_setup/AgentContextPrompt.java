package com.comagents.agentproxy.agent_setup;

import com.comagents.commons.domain.agent_context.AgentContext;
import com.comagents.commons.domain.agent_context.AgentContextItem;
import com.comagents.commons.domain.agent_context.GithubRepositoryAgentContextItem;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * A session's {@link AgentContext} as a section of the kick-off prompt, for the agent to use only if useful. The
 * cloned repository is left out: it is the working directory, already there. Other repositories and files are listed
 * by URL.
 */
public final class AgentContextPrompt {

    private static final Comparator<AgentContextItem> ORDER = Comparator
            .comparing(AgentContextItem::type)
            .thenComparing(AgentContextItem::getName);

    private AgentContextPrompt() {
    }

    /** The prompt section, or empty when the context has nothing besides the cloned repository. */
    public static Optional<String> render(AgentContext context) {
        if (context == null) {
            return Optional.empty();
        }
        Optional<GithubRepositoryAgentContextItem> cloned = context.repositoryToClone();
        List<String> lines = context.items().stream()
                .filter(item -> cloned.filter(item::equals).isEmpty())
                .sorted(ORDER)
                .map(AgentContextPrompt::line)
                .toList();
        if (lines.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of("Context of this session, besides the working directory. These items are not available "
                + "locally; use one only if it helps:\n" + String.join("\n", lines));
    }

    // e.g. - file "requirements.md" (acceptance criteria; not downloaded), download URL: https://...
    private static String line(AgentContextItem item) {
        return switch (item.type()) {
            case GITHUB_REPOSITORY -> "- repository \"" + item.getName() + "\"" + notes(item, "not cloned") + ": " + item.getValue();
            case FILE -> "- file \"" + item.getName() + "\"" + notes(item, "not downloaded") + ", download URL: " + item.getValue();
            case STRING -> "- text \"" + item.getName() + "\"" + notes(item, null) + ": " + item.getValue();
        };
    }

    // " (<description>; <status>)", either part optional
    private static String notes(AgentContextItem item, String status) {
        List<String> notes = Stream.concat(item.getDescription().stream(), Stream.ofNullable(status)).toList();
        return notes.isEmpty() ? "" : " (" + String.join("; ", notes) + ")";
    }
}
