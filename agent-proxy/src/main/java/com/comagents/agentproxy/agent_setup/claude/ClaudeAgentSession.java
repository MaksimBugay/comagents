package com.comagents.agentproxy.agent_setup.claude;

import com.comagents.agentproxy.domain.AgentSessionState;
import com.comagents.agentproxy.domain.AiAgent;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

/** One headless Claude Code run ({@code claude -p}) and its state. */
public final class ClaudeAgentSession {

    private static final SecureRandom TOKEN_SOURCE = new SecureRandom();

    private final UUID sessionId;
    private final AiAgent agent;
    private final Path workingDirectory;
    private final Path transcriptFile;
    private final Path responseFile;
    private final boolean resumed;
    private final ClaudeSessionOptions options;
    // the run's secret for agent-proxy's MCP server: only this run's Claude Code knows it (--mcp-config)
    private final String mcpToken = newToken();
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
    // the question the run ends its turn on, an answer that came before the run was archived, and whether it was;
    // guarded by this
    private String deferredQuestion;
    private String earlyAnswer;
    private boolean archivedForAnswer;

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

    /** Whether {@code token} is this run's MCP token; compared in constant time. */
    public boolean acceptsMcpToken(String token) {
        return token != null && MessageDigest.isEqual(mcpToken.getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8));
    }

    String mcpToken() {
        return mcpToken;
    }

    private static String newToken() {
        byte[] token = new byte[32];
        TOKEN_SOURCE.nextBytes(token);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(token);
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

    synchronized void deferQuestion(String question) {
        deferredQuestion = question;
        earlyAnswer = null;
    }

    synchronized void cancelDeferredQuestion() {
        deferredQuestion = null;
        earlyAnswer = null;
    }

    synchronized Optional<String> deferredQuestion() {
        return Optional.ofNullable(deferredQuestion);
    }

    /** Keeps the answer to the deferred question until the run is archived; false if there is none or it already is. */
    synchronized boolean keepEarlyAnswer(String answer) {
        if (deferredQuestion == null || archivedForAnswer) {
            return false;
        }
        earlyAnswer = answer;
        return true;
    }

    /** Marks the run archived for the answer: later answers go to the archive; returns one kept before. */
    synchronized Optional<String> archivedForAnswer() {
        archivedForAnswer = true;
        return Optional.ofNullable(earlyAnswer);
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
