package com.comagents.agentproxy.agent_setup.claude;

public enum ClaudeAgentSessionState {
    RUNNING,
    /** Claude Code exited normally and reported a successful result. */
    DONE,
    /** Claude Code exited with an error, or without a successful result. */
    FAILED,
    /** Stopped because it ran longer than the session time limit. */
    TIMED_OUT,
    /** Stopped on request ({@code killSession}). */
    KILLED;

    public boolean isFinished() {
        return this != RUNNING;
    }
}
