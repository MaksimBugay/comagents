package com.comagents.agentproxy.agent_setup.claude;

import com.comagents.agentproxy.domain.AgentSessionState;
import com.comagents.agentproxy.domain.AiAgent;
import com.comagents.agentproxy.workspace.FileTrees;
import com.comagents.agentproxy.workspace.SessionFolders;
import io.micronaut.context.annotation.Value;
import io.micronaut.json.JsonMapper;
import io.micronaut.json.tree.JsonNode;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Runs Claude Code headless ({@code claude -p}), one child process per session, and keeps track of the runs.
 * Design: {@code spec/claude_q_and_a.md}.
 * <p>
 * A run is started asynchronously in the session's working directory with the agent's kick-off prompt and our
 * session id as Claude's session id, so later runs can {@code --resume} it. Its stream-json output is written to a
 * transcript file and summarised into the log; the final answer is written to
 * {@code <session folder>/<agent name>/agent-response-<session id>.md}.
 */
@Singleton
public class ClaudeAgentSessionRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(ClaudeAgentSessionRegistry.class);

    // how long a stopped process gets to exit before it is killed forcibly
    private static final Duration STOP_GRACE_PERIOD = Duration.ofSeconds(10);

    // how often the watchdog checks the time limit and the idle timeout
    private static final Duration WATCHDOG_INTERVAL = Duration.ofSeconds(1);

    private final JsonMapper jsonMapper;
    private final String claudeExecutable;
    private final int maxConcurrentSessions;
    private final Duration sessionTimeout;
    private final Duration idleTimeout;
    private final Semaphore sessionSlots;
    private final Map<UUID, ClaudeAgentSession> sessions = new ConcurrentHashMap<>();
    // reserved, not yet started: a session id is either here or in sessions, never both
    private final Set<UUID> reservedSessionIds = ConcurrentHashMap.newKeySet();

    public ClaudeAgentSessionRegistry(JsonMapper jsonMapper,
                                      @Value("${claude.executable:claude}") String claudeExecutable,
                                      @Value("${claude.session.max-concurrent:2}") int maxConcurrentSessions,
                                      @Value("${claude.session.timeout:60m}") Duration sessionTimeout,
                                      @Value("${claude.session.idle-timeout:10m}") Duration idleTimeout) {
        this.jsonMapper = jsonMapper;
        this.claudeExecutable = claudeExecutable;
        this.maxConcurrentSessions = maxConcurrentSessions;
        this.sessionTimeout = sessionTimeout;
        this.idleTimeout = idleTimeout;
        this.sessionSlots = new Semaphore(maxConcurrentSessions);
    }

    /**
     * Takes a session slot and claims the session id, before any setup work is done. Use it with
     * try-with-resources: the slot is freed again on close unless {@link SessionSlot#start} handed it to a run.
     *
     * @throws ClaudeSessionLimitException when all concurrent session slots are busy
     * @throws IllegalStateException       when the session id is already reserved or registered
     */
    public SessionSlot reserve(UUID sessionId) {
        // start() adds to sessions before removing from reservedSessionIds, so one of the two checks always sees it
        if (!reservedSessionIds.add(sessionId)) {
            throw new IllegalStateException("Claude session " + sessionId + " is already being set up");
        }
        if (sessions.containsKey(sessionId)) {
            reservedSessionIds.remove(sessionId);
            throw new IllegalStateException("Claude session " + sessionId + " already exists");
        }
        if (!sessionSlots.tryAcquire()) {
            reservedSessionIds.remove(sessionId);
            throw new ClaudeSessionLimitException(maxConcurrentSessions);
        }
        return new SessionSlot(sessionId);
    }

    /** {@link #reserve} and {@link SessionSlot#start} in one step, for sessions that are already prepared. */
    public ClaudeAgentSession createSession(UUID sessionId, AiAgent agent, Path workingDirectory) {
        try (SessionSlot slot = reserve(sessionId)) {
            return slot.start(agent, workingDirectory);
        }
    }

    /** A reserved session slot; see {@link #reserve}. */
    public final class SessionSlot implements AutoCloseable {

        private final UUID sessionId;
        private boolean started;
        private boolean closed;

        private SessionSlot(UUID sessionId) {
            this.sessionId = sessionId;
        }

        /**
         * Starts the agent's kick-off prompt in {@code workingDirectory} and returns immediately; from here on the
         * run owns the slot. Setup must have prepared the session: the working directory and
         * {@code <session folder>/<agent name>} must exist, and the agent folder must hold the agent's
         * {@code agent.json} (model, effort, allowed tools, kick-off prompt).
         *
         * @throws IllegalStateException when the session is not prepared or Claude Code can't be started; the slot
         *                               stays reserved until {@link #close}
         */
        public ClaudeAgentSession start(AiAgent agent, Path workingDirectory) {
            if (started || closed) {
                throw new IllegalStateException("Slot for session " + sessionId + " was already " + (started ? "started" : "closed"));
            }
            ClaudeAgentSession session = launch(sessionId, agent, workingDirectory);
            sessions.put(sessionId, session);
            reservedSessionIds.remove(sessionId);
            started = true;
            LOG.info("[{} {}] started in {}", agent.getAgentName(), sessionId, workingDirectory);
            Thread.ofVirtual().name("claude-output-" + sessionId).start(() -> streamOutput(session));
            Thread.ofVirtual().name("claude-watchdog-" + sessionId).start(() -> watch(session));
            return session;
        }

        /** Frees the slot and the session id if the run was never started; otherwise does nothing. */
        @Override
        public void close() {
            if (!started && !closed) {
                reservedSessionIds.remove(sessionId);
                sessionSlots.release();
            }
            closed = true;
        }
    }

    private ClaudeAgentSession launch(UUID sessionId, AiAgent agent, Path workingDirectory) {
        Path agentFolder = SessionFolders.agentFolder(sessionId, agent);
        requirePrepared(sessionId, workingDirectory, agentFolder);
        List<String> command = command(readConfig(agent, agentFolder), sessionId);
        ClaudeAgentSession session = new ClaudeAgentSession(sessionId, agent, workingDirectory,
                agentFolder.resolve("claude-stream.jsonl"), SessionFolders.agentResponseFile(sessionId, agent));
        try {
            session.attach(new ProcessBuilder(command)
                    .directory(workingDirectory.toFile())
                    // nothing is piped in: without this, claude -p waits 3 s for stdin data on every run
                    .redirectInput(ProcessBuilder.Redirect.from(new File("/dev/null")))
                    .redirectErrorStream(true)
                    .start());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to start Claude Code for session " + sessionId, e);
        }
        return session;
    }

    public Optional<ClaudeAgentSession> findSession(UUID sessionId) {
        return Optional.ofNullable(sessions.get(sessionId));
    }

    /**
     * Marks the session {@code WAITING} until the returned handle is closed, so the idle timeout
     * leaves it alone; closing it returns the session to {@code RUNNING} and restarts the idle timer. Use with
     * try-with-resources around the wait. Kill, the session time limit and process exit still apply while waiting.
     *
     * @throws IllegalStateException when the session is unknown or no longer running
     */
    public Waiting markWaiting(UUID sessionId) {
        ClaudeAgentSession session = sessions.get(sessionId);
        if (session == null || session.getState().isFinished()) {
            throw new IllegalStateException("Claude session " + sessionId + " is not running");
        }
        session.startWaiting();
        return new Waiting(session);
    }

    /** See {@link #markWaiting}. */
    public static final class Waiting implements AutoCloseable {

        private final ClaudeAgentSession session;
        private boolean closed;

        private Waiting(ClaudeAgentSession session) {
            this.session = session;
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                session.stopWaiting();
            }
        }
    }

    /** Stops a running or waiting session; does nothing if it is unknown or already finished. */
    public void killSession(UUID sessionId) {
        ClaudeAgentSession session = sessions.get(sessionId);
        if (session != null && session.finish(AgentSessionState.KILLED)) {
            LOG.info("[{} {}] killing", session.getAgent().getAgentName(), sessionId);
            stop(session.process());
        }
    }

    /**
     * Stops the session if it is running, forgets it, and deletes its session folder (cloned repository, agent
     * files, transcript, response) and Claude Code's own record of the conversation, so it can't be resumed.
     */
    public void cleanAllSessionData(UUID sessionId) {
        killSession(sessionId);
        ClaudeAgentSession session = sessions.get(sessionId);
        if (session != null) {
            awaitFinished(session);
            sessions.remove(sessionId);
        }
        FileTrees.deleteIfExists(SessionFolders.of(sessionId));
        deleteClaudeConversation(sessionId);
        LOG.info("[{}] session data deleted", sessionId);
    }

    @PreDestroy
    void killAllSessions() {
        sessions.keySet().forEach(this::killSession);
    }

    private static void requirePrepared(UUID sessionId, Path... folders) {
        for (Path folder : folders) {
            if (!Files.isDirectory(folder)) {
                throw new IllegalStateException("Session " + sessionId + " is not prepared: " + folder + " does not exist");
            }
        }
    }

    private List<String> command(ClaudeAgentConfig config, UUID sessionId) {
        List<String> command = new ArrayList<>(List.of(
                claudeExecutable, "-p", config.kickOffPrompt(),
                "--session-id", sessionId.toString(),
                "--output-format", "stream-json", "--verbose",
                // nobody can answer Claude Code's built-in question tool in a headless run
                "--disallowedTools", "AskUserQuestion"));
        if (config.model() != null) {
            command.addAll(List.of("--model", config.model()));
        }
        if (config.effort() != null) {
            command.addAll(List.of("--effort", config.effort()));
        }
        if (!config.allowedTools().isEmpty()) {
            command.add("--allowedTools");
            command.addAll(config.allowedTools());
        }
        return command;
    }

    private ClaudeAgentConfig readConfig(AiAgent agent, Path agentFolder) {
        Path configFile = agentFolder.resolve(ClaudeAgentConfig.FILE_NAME);
        try {
            ClaudeAgentConfig config = jsonMapper.readValue(Files.readAllBytes(configFile), ClaudeAgentConfig.class);
            if (config == null || config.kickOffPrompt() == null || config.kickOffPrompt().isBlank()) {
                throw new IllegalStateException(configFile + " has no kickOffPrompt");
            }
            return config;
        } catch (IOException e) {
            throw new IllegalStateException("Agent " + agent.getAgentName() + " has no readable " + configFile, e);
        }
    }


    // reading continuously also keeps Claude Code from stalling on a full output pipe
    private void streamOutput(ClaudeAgentSession session) {
        try (BufferedReader output = session.process().inputReader(StandardCharsets.UTF_8);
             BufferedWriter transcript = Files.newBufferedWriter(session.getTranscriptFile(), StandardCharsets.UTF_8)) {
            for (String line; (line = output.readLine()) != null; ) {
                transcript.write(line);
                transcript.newLine();
                transcript.flush();
                session.recordOutput();
                handleOutputLine(session, line);
            }
        } catch (IOException | RuntimeException e) {
            LOG.error("[{} {}] reading Claude Code output failed", session.getAgent().getAgentName(), session.getSessionId(), e);
        } finally {
            onSessionFinishedHandler(session.getSessionId());
        }
    }

    private void handleOutputLine(ClaudeAgentSession session, String line) {
        String agentName = session.getAgent().getAgentName();
        Optional<JsonNode> parsed = parseEvent(line);
        if (parsed.isEmpty()) {
            // not an event: stderr is merged into the same stream (warnings, errors)
            LOG.info("[{} {}] {}", agentName, session.getSessionId(), ClaudeStreamEvents.truncate(line));
            return;
        }
        JsonNode event = parsed.get();
        ClaudeStreamEvents.summarize(event)
                .ifPresent(summary -> LOG.info("[{} {}] {}", agentName, session.getSessionId(), summary));
        if (ClaudeStreamEvents.isResult(event)) {
            session.recordResult(ClaudeStreamEvents.resultText(event).orElse(null), ClaudeStreamEvents.isSuccessfulResult(event));
        }
    }

    // empty for lines that are not JSON, and for blank or "null" lines, which parse to no value
    private Optional<JsonNode> parseEvent(String line) {
        try {
            return Optional.ofNullable(jsonMapper.readValue(line, JsonNode.class));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /**
     * Enforces two limits until the process exits:
     * <ul>
     *   <li>{@code claude.session.timeout}: running too long, waiting included, stops the session
     *       ({@code TIMED_OUT}); its files stay for {@code /agent-status} and {@code /agent-response}.</li>
     *   <li>{@code claude.session.idle-timeout}: a {@code RUNNING} session without output for that long is stuck, so
     *       it is stopped ({@code TIMED_OUT}) and all its data deleted, as with {@link #cleanAllSessionData}.
     *       A {@code WAITING} session is silent on purpose and exempt.</li>
     * </ul>
     */
    private void watch(ClaudeAgentSession session) {
        String agentName = session.getAgent().getAgentName();
        UUID sessionId = session.getSessionId();
        Instant deadline = session.getStartedAt().plus(sessionTimeout);
        while (!waitForExit(session.process(), WATCHDOG_INTERVAL)) {
            Instant now = Instant.now();
            if (now.isAfter(deadline)) {
                if (session.finish(AgentSessionState.TIMED_OUT)) {
                    LOG.warn("[{} {}] still running after {}, stopping", agentName, sessionId, sessionTimeout);
                    stop(session.process());
                }
                return;
            }
            // the compare-and-set fails for a WAITING session, which is then left alone
            if (now.isAfter(session.getLastOutputAt().plus(idleTimeout))
                    && session.transition(AgentSessionState.RUNNING, AgentSessionState.TIMED_OUT)) {
                LOG.warn("[{} {}] no output for {}, stopping and deleting the session", agentName, sessionId, idleTimeout);
                stop(session.process());
                cleanAllSessionData(sessionId);
                return;
            }
        }
    }

    // runs once per session, on the output thread, after Claude Code closed its output
    private void onSessionFinishedHandler(UUID sessionId) {
        ClaudeAgentSession session = sessions.get(sessionId);
        if (session == null) {
            return;
        }
        Process process = session.process();
        int exitCode = waitForExit(process, STOP_GRACE_PERIOD) ? process.exitValue() : forceStop(process);
        boolean succeeded = exitCode == 0 && session.isResultSuccessful();
        session.finish(succeeded ? AgentSessionState.DONE : AgentSessionState.FAILED);
        session.resultText().ifPresent(text -> writeResponse(session, text));
        sessionSlots.release();
        session.markFinished();
        LOG.info("[{} {}] finished: {}, exit code {}", session.getAgent().getAgentName(), sessionId, session.getState(), exitCode);
    }

    // written to a temporary file and renamed, so a reader never sees a half-written response
    private void writeResponse(ClaudeAgentSession session, String text) {
        Path responseFile = session.getResponseFile();
        Path partial = responseFile.resolveSibling(responseFile.getFileName() + ".partial");
        try {
            Files.writeString(partial, text, StandardCharsets.UTF_8);
            Files.move(partial, responseFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOG.error("[{} {}] writing {} failed", session.getAgent().getAgentName(), session.getSessionId(), session.getResponseFile(), e);
        }
    }

    // SIGTERM to Claude Code and the commands it started (mvn, scripts), then SIGKILL after the grace period
    private static void stop(Process process) {
        process.descendants().forEach(ProcessHandle::destroy);
        process.destroy();
        if (!waitForExit(process, STOP_GRACE_PERIOD)) {
            forceStop(process);
        }
    }

    private static int forceStop(Process process) {
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
        try {
            return process.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return -1;
        }
    }

    private static boolean waitForExit(Process process, Duration timeout) {
        try {
            return process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static void awaitFinished(ClaudeAgentSession session) {
        try {
            session.awaitFinished(STOP_GRACE_PERIOD.multipliedBy(3));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Claude Code keeps each conversation (what {@code --resume} reads) as {@code <session id>.jsonl}, plus an
     * optional {@code <session id>/} folder, in a per-project folder under {@code <config dir>/projects}.
     * Matching by file name avoids depending on how project folders are named. Best effort.
     */
    private static void deleteClaudeConversation(UUID sessionId) {
        String configDir = System.getenv("CLAUDE_CONFIG_DIR");
        Path projects = (configDir != null ? Path.of(configDir) : Path.of(System.getProperty("user.home"), ".claude")).resolve("projects");
        if (!Files.isDirectory(projects)) {
            return;
        }
        try (Stream<Path> projectFolders = Files.list(projects)) {
            projectFolders.filter(Files::isDirectory).forEach(project -> {
                FileTrees.deleteIfExists(project.resolve(sessionId + ".jsonl"));
                FileTrees.deleteIfExists(project.resolve(sessionId.toString()));
            });
        } catch (IOException | UncheckedIOException e) {
            LOG.warn("[{}] deleting Claude Code's conversation files failed", sessionId, e);
        }
    }
}
