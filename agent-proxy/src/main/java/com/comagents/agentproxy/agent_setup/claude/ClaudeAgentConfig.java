package com.comagents.agentproxy.agent_setup.claude;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

/**
 * How agent-proxy runs a Claude Code agent: the agent's {@code agent.json}, kept with its other files in the
 * comagents sources ({@code agent/claude_code/<agent name>/agent.json}).
 *
 * @param model         {@code --model}: an alias such as {@code opus} or a full model name; Claude Code's default if null
 * @param effort        {@code --effort}: {@code low}, {@code medium}, {@code high}, {@code xhigh} or {@code max};
 *                      Claude Code's default if null
 * @param kickOffPrompt the first instruction of every session
 */
@Serdeable
public record ClaudeAgentConfig(@Nullable String model, @Nullable String effort, String kickOffPrompt) {

    public static final String FILE_NAME = "agent.json";
}
