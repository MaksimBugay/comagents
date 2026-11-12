package com.comagents.agentproxy.domain;

/** Lifecycle of an agent run, whatever harness runs it. */
public enum AgentSessionState {
    RUNNING,
    /** Paused while waiting for something outside the agent; silence is expected, so the idle timeout does not apply. */
    WAITING,
    /** The agent exited normally and reported a successful result. */
    DONE,
    /** The agent exited with an error, or without a successful result. */
    FAILED,
    /** Stopped because it ran longer than the session time limit. */
    TIMED_OUT,
    /** Stopped on request ({@code killSession}). */
    KILLED,
    /** Kept for a later resume ({@code archive}): stopped if it was running, state saved, never cleaned up automatically. */
    ARCHIVED;

    /** Neither running nor waiting: the agent process has ended. */
    public boolean isFinished() {
        return this != RUNNING && this != WAITING;
    }
}
