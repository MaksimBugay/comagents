package com.comagents.agentproxy.agent_setup.claude;

import com.comagents.agentproxy.domain.AgentSessionState;
import com.comagents.agentproxy.domain.AiAgent;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

/** One headless Claude Code run ({@code claude -p}) and its state. */
public final class ClaudeAgentSession {

    private final UUID sessionId;
    private final AiAgent agent;
    private final Path workingDirectory;
    private final Path transcriptFile;
    private final Path responseFile;
    private final boolean resumed;
    private final ClaudeSessionOptions options;
    // the last line reported for this session; written and read by the output thread only
    private String lastReportedOutput;
    private final Instant startedAt = Instant.now();

    private final AtomicReference<AgentSessionState> state = new AtomicReference<>(AgentSessionState.RUNNING);
    private final CompletableFuture<AgentSessionState> finished = new CompletableFuture<>();

    private volatile Process process;
    private volatile String resultText;
    private volatile boolean resultSuccessful;
    private volatile Instant finishedAt;
    private volatile Instant lastOutputAt = startedAt;
    // overlapping waits keep the session WAITING until the last one ends; guarded by this
    private int pendingWaits;

    ClaudeAgentSession(UUID sessionId, AiAgent agent, Path workingDirectory, Path transcriptFile, Path responseFile,
                       boolean resumed, ClaudeSessionOptions options) {
        this.resumed = resumed;
        this.options = options;
        this.sessionId = sessionId;
        this.agent = agent;
        this.workingDirectory = workingDirectory;
        this.transcriptFile = transcriptFile;
        this.responseFile = responseFile;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public AiAgent getAgent() {
        return agent;
    }

    public Path getWorkingDirectory() {
        return workingDirectory;
    }

    /** Every stream-json event Claude Code printed, one per line. */
    public Path getTranscriptFile() {
        return transcriptFile;
    }

    /** The final answer, written when the run ends with a result. */
    public Path getResponseFile() {
        return responseFile;
    }

    /** Whether this run continues an archived session ({@code resume}) rather than starting a new one. */
    public boolean isResumed() {
        return resumed;
    }

    /** Whether thinking and thinking-token progress are shown (logged at INFO and reported), not only logged at DEBUG. */
    public boolean isVerboseOutput() {
        return options.verboseOutput();
    }

    public ClaudeSessionOptions getOptions() {
        return options;
    }

    /** Remembers {@code output} as the session's last reported line; returns whether it repeats the one before. */
    boolean reportOutput(String output) {
        boolean repeated = output.equals(lastReportedOutput);
        lastReportedOutput = output;
        return repeated;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    /** When Claude Code last printed anything (an event or a stderr line); the start time before that. */
    public Instant getLastOutputAt() {
        return lastOutputAt;
    }

    public Optional<Instant> getFinishedAt() {
        return Optional.ofNullable(finishedAt);
    }

    public AgentSessionState getState() {
        return state.get();
    }

    /**
     * Waits until the run has ended and the finish handler has run.
     *
     * @return the final state, or empty if the run is still going after {@code timeout}
     */
    public Optional<AgentSessionState> awaitFinished(Duration timeout) throws InterruptedException {
        try {
            return Optional.of(finished.get(timeout.toMillis(), TimeUnit.MILLISECONDS));
        } catch (TimeoutException e) {
            return Optional.empty();
        } catch (ExecutionException e) {
            throw new IllegalStateException("Session " + sessionId + " finished exceptionally", e.getCause());
        }
    }

    Process process() {
        return process;
    }

    void recordOutput() {
        lastOutputAt = Instant.now();
    }

    void attach(Process process) {
        this.process = process;
    }

    /** Moves from {@code expected} to {@code next}; false if the state was already something else. */
    boolean transition(AgentSessionState expected, AgentSessionState next) {
        return state.compareAndSet(expected, next);
    }

    /** Marks the session {@code ARCHIVED} whatever its state; returns the state it had before. */
    AgentSessionState archive() {
        return state.getAndSet(AgentSessionState.ARCHIVED);
    }

    /** Moves from {@code RUNNING} or {@code WAITING} to the final state {@code next}; false if already finished. */
    boolean finish(AgentSessionState next) {
        return transition(AgentSessionState.RUNNING, next) || transition(AgentSessionState.WAITING, next);
    }

    synchronized void startWaiting() {
        if (pendingWaits++ == 0 && transition(AgentSessionState.RUNNING, AgentSessionState.WAITING)) {
            // the idle timeout for the wait counts from its start, not from the last output before it
            recordOutput();
        }
    }

    synchronized void stopWaiting() {
        if (--pendingWaits == 0 && transition(AgentSessionState.WAITING, AgentSessionState.RUNNING)) {
            // the idle timer starts over: the wait was not idleness
            recordOutput();
        }
    }

    void recordResult(String text, boolean successful) {
        this.resultText = text;
        this.resultSuccessful = successful;
    }

    Optional<String> resultText() {
        return Optional.ofNullable(resultText);
    }

    boolean isResultSuccessful() {
        return resultSuccessful;
    }

    void markFinished() {
        finishedAt = Instant.now();
        finished.complete(state.get());
    }
}
