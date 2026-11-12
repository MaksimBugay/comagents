package com.comagents.agentproxy.agent_setup.claude;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

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
 */
@Serdeable
public record ClaudeAgentConfig(@Nullable String model, @Nullable String effort, @Nullable List<String> allowedTools,
                                String kickOffPrompt) {

    public static final String FILE_NAME = "agent.json";

    public ClaudeAgentConfig {
        allowedTools = allowedTools == null ? List.of() : List.copyOf(allowedTools);
    }
}
