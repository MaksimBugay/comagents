package com.comagents.agentproxy.agent_setup.claude;

import com.comagents.agentproxy.domain.AgentSessionState;
import com.comagents.agentproxy.domain.AiAgent;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.UUID;

/**
 * The registry's state of an archived session, saved as {@value #STATE_FILE} in {@code <session folder>/<agent name>/},
 * next to Claude Code's own state for the session ({@code claude-state/}). The session id doubles as Claude Code's
 * session id, so a later {@code claude -p "<prompt>" --resume <session id>} run in {@code workingDirectory}, with
 * {@code CLAUDE_CONFIG_DIR} pointing at that {@code claude-state/}, continues the conversation.
 *
 * @param stateBeforeArchive the state when the session was archived: {@code RUNNING}, {@code WAITING}, or a final
 *                           state if the run had already ended
 * @param resultText         the agent's final answer, if it gave one before being archived
 * @param verboseOutput      whether the session shows thinking; a resumed run keeps showing it
 * @param actorAccountId     the Pushca account that started the session: its chat is reopened for it after a restart
 */
@Serdeable
public record ClaudeSessionArchive(UUID sessionId, AiAgent agent, String workingDirectory,
                                   AgentSessionState stateBeforeArchive, Instant startedAt, Instant lastOutputAt,
                                   Instant archivedAt, @Nullable String resultText, boolean verboseOutput,
                                   @Nullable String actorAccountId) {

    public static final String STATE_FILE = "session-state.json";

    public ClaudeSessionOptions options() {
        return new ClaudeSessionOptions(verboseOutput, actorAccountId);
    }
}
