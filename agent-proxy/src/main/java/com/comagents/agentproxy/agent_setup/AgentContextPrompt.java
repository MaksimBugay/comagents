package com.comagents.agentproxy.agent_setup;

import com.comagents.agentproxy.domain.agent_context.AgentContext;
import com.comagents.agentproxy.domain.agent_context.AgentContextItem;
import com.comagents.agentproxy.domain.agent_context.AgentContextItemAttributes;
import org.apache.commons.lang3.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * A session's {@link AgentContext} as a section of the kick-off prompt. Only the required repository is local (the
 * working directory); other repositories and files are listed by URL, for the agent to fetch only if useful.
 */
public final class AgentContextPrompt {

    private static final Comparator<AgentContextItem> ORDER = Comparator
            .comparing((AgentContextItem item) -> item.attributes().type())
            .thenComparing(item -> item.attributes().name(), Comparator.nullsFirst(Comparator.naturalOrder()));

    private AgentContextPrompt() {
    }

    /** The prompt section, or empty when the context has no item with a value. */
    public static Optional<String> render(AgentContext context) {
        List<String> lines = context == null || context.items() == null ? List.of() : context.items().stream()
                .filter(item -> StringUtils.isNotBlank(item.value()))
                .sorted(ORDER)
                .map(AgentContextPrompt::line)
                .toList();
        if (lines.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of("Context of this session. Only the working directory is available locally; the other items "
                + "are listed for reference, use one only if it helps:\n" + String.join("\n", lines));
    }

    private static String line(AgentContextItem item) {
        AgentContextItemAttributes attributes = item.attributes();
        return switch (attributes.type()) {
            case REPOSITORY -> attributes.required()
                    ? "- repository \"" + attributes.name() + "\", cloned into the working directory: " + item.value()
                    : "- repository \"" + attributes.name() + "\" (not cloned): " + item.value();
            case FILE -> "- file \"" + attributes.name() + "\" (not downloaded), download URL: " + item.value();
            case STRING -> "- text \"" + attributes.name() + "\": " + item.value();
        };
    }
}
