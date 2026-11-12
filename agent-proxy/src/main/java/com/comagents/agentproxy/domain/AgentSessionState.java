package com.comagents.agentproxy.domain;

/** Lifecycle of an agent run, whatever harness runs it. */
public enum AgentSessionState {
    RUNNING,
    /** The agent exited normally and reported a successful result. */
    DONE,
    /** The agent exited with an error, or without a successful result. */
    FAILED,
    /** Stopped because it ran longer than the session time limit. */
    TIMED_OUT,
    /** Stopped on request ({@code killSession}). */
    KILLED;

    public boolean isFinished() {
        return this != RUNNING;
    }
}
