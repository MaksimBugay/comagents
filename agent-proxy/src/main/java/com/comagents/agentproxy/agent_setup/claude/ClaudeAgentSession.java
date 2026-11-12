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
    private final Instant startedAt = Instant.now();

    private final AtomicReference<AgentSessionState> state = new AtomicReference<>(AgentSessionState.RUNNING);
    private final CompletableFuture<AgentSessionState> finished = new CompletableFuture<>();

    private volatile Process process;
    private volatile String resultText;
    private volatile boolean resultSuccessful;
    private volatile Instant finishedAt;

    ClaudeAgentSession(UUID sessionId, AiAgent agent, Path workingDirectory, Path transcriptFile, Path responseFile) {
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

    public Instant getStartedAt() {
        return startedAt;
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

    void attach(Process process) {
        this.process = process;
    }

    /** Moves from {@code expected} to {@code next}; false if the state was already something else. */
    boolean transition(AgentSessionState expected, AgentSessionState next) {
        return state.compareAndSet(expected, next);
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
