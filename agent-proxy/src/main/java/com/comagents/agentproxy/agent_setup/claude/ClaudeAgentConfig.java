package com.comagents.agentproxy.agent_setup.claude;

import com.comagents.agentproxy.domain.agent_context.RequiredAgentContextItem;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;
import java.util.Set;

/**
 * How agent-proxy runs a Claude Code agent: the agent's {@code agent.json}, kept with its other files in the
 * comagents sources ({@code agent/claude_code/<agent name>/agent.json}).
 *
 * @param model         {@code --model}: an alias such as {@code opus} or a full model name; Claude Code's default if null
 * @param effort        {@code --effort}: {@code low}, {@code medium}, {@code high}, {@code xhigh} or {@code max};
 *                      Claude Code's default if null
 * @param allowedTools  {@code --allowedTools}: tools the agent may use without approval, in permission-rule syntax.
 *                      They live here rather than in the agent's {@code .claude/settings.json} because a headless run
 *                      never trusts its folder, so Claude Code ignores (and warns about) a project's allow rules
 * @param kickOffPrompt the first instruction of every session
 * @param resumePrompt  the instruction when an archived session is resumed; {@link #DEFAULT_RESUME_PROMPT} if null
 * @param requiredItems the agent context items the agent can't work without (type and/or name): a start request
 *                      without them is rejected; none if null
 * @param responseSchema {@code --json-schema}: the JSON Schema of the agent's final answer, which must describe an
 *                      agent context ({@code {"items": [...]}}) and is read as the session's response; if null the
 *                      final text is the response, as one {@code STRING} item named
 *                      {@value #DEFAULT_RESPONSE_ITEM}
 */
@Serdeable
public record ClaudeAgentConfig(@Nullable String model, @Nullable String effort, @Nullable List<String> allowedTools,
                                String kickOffPrompt, @Nullable String resumePrompt,
                                @Nullable Set<RequiredAgentContextItem> requiredItems,
                                @Nullable JsonNode responseSchema) {

    public static final String FILE_NAME = "agent.json";

    public static final String DEFAULT_RESPONSE_ITEM = "response";

    public static final String DEFAULT_RESUME_PROMPT = "You were stopped before you finished. Continue the task from where "
            + "you stopped, and return your final answer as you would have.";

    public ClaudeAgentConfig {
        allowedTools = allowedTools == null ? List.of() : List.copyOf(allowedTools);
        resumePrompt = resumePrompt == null || resumePrompt.isBlank() ? DEFAULT_RESUME_PROMPT : resumePrompt;
        requiredItems = requiredItems == null ? Set.of() : Set.copyOf(requiredItems);
    }
}
