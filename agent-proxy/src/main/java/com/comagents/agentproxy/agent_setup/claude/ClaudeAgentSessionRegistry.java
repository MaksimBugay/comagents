package com.comagents.agentproxy.agent_setup.claude;

import com.comagents.agentproxy.domain.AgentResponseReady;
import com.comagents.agentproxy.workspace.FileTrees;
import com.comagents.agentproxy.workspace.SessionFolders;
import com.comagents.commons.domain.AgentSessionState;
import com.comagents.commons.domain.AiAgent;
import com.comagents.commons.domain.agent_context.AgentContext;
import com.comagents.commons.domain.agent_context.StringAgentContextItem;
import com.comagents.commons.exception.ClaudeSessionExistsException;
import com.comagents.commons.exception.ClaudeSessionLimitException;
import com.comagents.commons.serialization.JsonUtility;
import com.comagents.commons.serialization.SerializationException;
import io.micronaut.context.annotation.Value;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.core.annotation.Nullable;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Runs Claude Code headless ({@code claude -p}), one child process per session, and keeps track of the runs.
 * <p>
 * A run is started asynchronously in the session's working directory with the agent's kick-off prompt and our
 * session id as Claude's session id, so later runs can {@code --resume} it. Its stream-json output is written to a
 * transcript file and summarised into the log; the final answer, as an agent context (see
 * {@link ClaudeAgentConfig#responseSchema()}), is written to
 * {@code <session folder>/<agent name>/agent-response-<session id>.json}.
 */
@Singleton
public class ClaudeAgentSessionRegistry implements ApplicationEventListener<StartupEvent> {

    private static final Logger LOG = LoggerFactory.getLogger(ClaudeAgentSessionRegistry.class);

    // how long a stopped process gets to exit before it is killed forcibly
    private static final Duration STOP_GRACE_PERIOD = Duration.ofSeconds(10);

    /** Claude Code's config dir ({@code CLAUDE_CONFIG_DIR}) inside each session's agent folder. */
    public static final String CLAUDE_STATE_FOLDER = "claude-state";

    /** The run's temp folder ({@code TMPDIR}) inside each session's agent folder. */
    public static final String TEMP_FOLDER = "tmp";

    /** The run's Maven folder inside each session's agent folder: its local repository and the wrapper's downloads. */
    public static final String MAVEN_FOLDER = "m2";

    /**
     * The response of the agent call an archived session waits for, stored in its agent folder when it came while all
     * session slots were busy: the session is resumed with it once one is free.
     */
    public static final String AGENT_CALL_RESPONSE_FILE = "agent-call-response.json";

    // see isInheritedByClaudeCode
    private static final Set<String> INHERITED_ENVIRONMENT = Set.of("PATH", "HOME", "LANG", "LC_ALL", "TZ",
            "DISABLE_AUTOUPDATER");
    private static final List<String> INHERITED_ENVIRONMENT_PREFIXES = List.of("ANTHROPIC_", "CLAUDE_CODE_");

    // how often the watchdog checks the time limit and the idle timeout
    private static final Duration WATCHDOG_INTERVAL = Duration.ofSeconds(1);

    private final String claudeExecutable;
    private final int maxConcurrentSessions;
    private final Duration sessionTimeout;
    private final Duration idleTimeout;
    private final SessionFolders sessionFolders;
    private final ClaudeSessionListener sessionListener;
    private final ApplicationEventPublisher<AgentResponseReady> responseReadyPublisher;
    private final AgentProxyMcpServer mcpServer;
    private final Semaphore sessionSlots;
    private final Map<UUID, ClaudeAgentSession> sessions = new ConcurrentHashMap<>();
    // reserved, not yet started: a session id is either here or in sessions, never both
    private final Set<UUID> reservedSessionIds = ConcurrentHashMap.newKeySet();

    public ClaudeAgentSessionRegistry(@Value("${claude.executable:claude}") String claudeExecutable,
                                      @Value("${claude.session.max-concurrent:3}") int maxConcurrentSessions,
                                      @Value("${claude.session.timeout:60m}") Duration sessionTimeout,
                                      @Value("${claude.session.idle-timeout:10m}") Duration idleTimeout,
                                      SessionFolders sessionFolders,
                                      ClaudeSessionListener sessionListener,
                                      AgentProxyMcpServer mcpServer,
                                      ApplicationEventPublisher<AgentResponseReady> responseReadyPublisher) {
        this.sessionFolders = sessionFolders;
        this.sessionListener = sessionListener;
        this.responseReadyPublisher = responseReadyPublisher;
        this.mcpServer = mcpServer;
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
     * @throws ClaudeSessionExistsException when the session id is already reserved or registered
     */
    public SessionSlot reserve(UUID sessionId) {
        // start() adds to sessions before removing from reservedSessionIds, so one of the two checks always sees it
        if (!reservedSessionIds.add(sessionId)) {
            throw new ClaudeSessionExistsException(sessionId, "is already being set up");
        }
        if (sessions.containsKey(sessionId)) {
            reservedSessionIds.remove(sessionId);
            throw new ClaudeSessionExistsException(sessionId, "already exists");
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
            return start(agent, workingDirectory, ClaudeSessionOptions.DEFAULT);
        }

        /** {@link #start(AiAgent, Path)} with the session's options, which its archive keeps for a resume. */
        public ClaudeAgentSession start(AiAgent agent, Path workingDirectory, ClaudeSessionOptions options) {
            return startRun(agent, workingDirectory, false, null, null, options);
        }

        /**
         * {@link #start(AiAgent, Path, ClaudeSessionOptions)} with {@code promptContext} (e.g. the session's context)
         * appended to the agent's kick-off prompt; null for none.
         */
        public ClaudeAgentSession start(AiAgent agent, Path workingDirectory, ClaudeSessionOptions options,
                                        @Nullable String promptContext) {
            return startRun(agent, workingDirectory, false, null, promptContext, options);
        }

        // prompt: null for the agent's own kick-off or resume prompt; promptContext: appended to the kick-off prompt
        private ClaudeAgentSession startRun(AiAgent agent, Path workingDirectory, boolean resume, @Nullable String prompt,
                                            @Nullable String promptContext, ClaudeSessionOptions options) {
            if (started || closed) {
                throw new IllegalStateException("Slot for session " + sessionId + " was already " + (started ? "started" : "closed"));
            }
            ClaudeAgentSession session = launch(sessionId, agent, workingDirectory, resume, prompt, promptContext, options);
            sessions.put(sessionId, session);
            reservedSessionIds.remove(sessionId);
            started = true;
            LOG.info("[{} {}] {} in {}", agent.getAgentName(), sessionId, resume ? "resumed" : "started", workingDirectory);
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

    private ClaudeAgentSession launch(UUID sessionId, AiAgent agent, Path workingDirectory, boolean resume,
                                      @Nullable String prompt, @Nullable String promptContext,
                                      ClaudeSessionOptions options) {
        Path agentFolder = sessionFolders.agentFolder(sessionId, agent);
        requirePrepared(sessionId, workingDirectory, agentFolder);
        ClaudeAgentConfig config = readConfig(agent, agentFolder);
        Path mavenFolder = createFolder(sessionId, agentFolder.resolve(MAVEN_FOLDER));
        ClaudeAgentSession session = new ClaudeAgentSession(sessionId, agent, workingDirectory,
                agentFolder.resolve("claude-stream.jsonl"), sessionFolders.agentResponseFile(sessionId, agent), resume,
                config.responseSchema() != null, options);
        ProcessBuilder processBuilder = new ProcessBuilder(command(config, session, mavenFolder, prompt, promptContext));
        processBuilder.environment().keySet().removeIf(name -> !isInheritedByClaudeCode(name));
        // Claude Code's own state for this session (settings, the conversation --resume reads, .claude.json) lives in
        // the session's agent folder: deleted with the session, kept with an archive, never shared between sessions
        processBuilder.environment().put("CLAUDE_CONFIG_DIR", agentFolder.resolve(CLAUDE_STATE_FOLDER).toString());
        // its own temp folder, in place of the container's shared one: parallel sessions never share temp files, and they
        // go with the session
        processBuilder.environment().put("TMPDIR", createFolder(sessionId, agentFolder.resolve(TEMP_FOLDER)).toString());
        // its own Maven repository and wrapper downloads, in place of the shared ~/.m2: parallel sessions never overwrite
        // each other's artifacts (the same SNAPSHOT installed by both, a download in progress), and they go with the
        // session
        processBuilder.environment().put("MAVEN_OPTS", "-Dmaven.repo.local=" + mavenFolder.resolve("repository"));
        processBuilder.environment().put("MAVEN_USER_HOME", mavenFolder.toString());
        if (resume && prompt == null) {
            // documented by Claude Code: finish the turn that archive() interrupted before following the new prompt.
            // Not for an answer: the interrupted turn ends on the "no answer yet" result, which must not be acted on
            processBuilder.environment().put("CLAUDE_CODE_RESUME_INTERRUPTED_TURN", "1");
        }
        try {
            session.attach(processBuilder
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

    /** How many sessions run at once at most: {@code claude.session.max-concurrent}. */
    public int maxSlots() {
        return maxConcurrentSessions;
    }

    /** How many more sessions can start: the concurrent session limit minus the sessions running, waiting or being set up. */
    public int freeSlots() {
        return sessionSlots.availablePermits();
    }

    /**
     * How many sessions are archived on disk, also from before a restart. They take no slot until resumed (archiving
     * frees the run's slot), and each takes one again on resume.
     */
    public int archivedSessions() {
        try (Stream<Path> sessionFolderStream = Files.list(sessionFolders.root())) {
            return (int) sessionFolderStream.filter(Files::isDirectory)
                    .map(ClaudeAgentSessionRegistry::sessionIdOf)
                    .flatMap(Optional::stream)
                    .filter(sessionId -> findArchive(sessionId).isPresent())
                    .count();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to list " + sessionFolders.root(), e);
        }
    }

    public Optional<ClaudeAgentSession> findSession(UUID sessionId) {
        return Optional.ofNullable(sessions.get(sessionId));
    }

    /**
     * Marks the session {@code WAITING} until the returned handle is closed. A waiting session silent for longer than
     * the idle timeout, counted from when the wait started, is archived rather than deleted (see {@link #archive}).
     * Closing the handle returns the session to {@code RUNNING} and restarts the idle timer. Use with
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
     * Stops the session if it is running, forgets it, and deletes its session folder: cloned repository, agent
     * files, transcript, response, and Claude Code's own state for it, so it can't be resumed.
     */
    public void cleanAllSessionData(UUID sessionId) {
        killSession(sessionId);
        ClaudeAgentSession session = sessions.get(sessionId);
        if (session != null) {
            awaitFinished(session);
            sessions.remove(sessionId);
        }
        FileTrees.deleteIfExists(sessionFolders.of(sessionId));
        LOG.info("[{}] session data deleted", sessionId);
        sessionListener.onSessionDataDeleted(sessionId);
    }

    /**
     * Deletes, as {@link #cleanAllSessionData} does, the sessions that finished before {@code cutoff}, whether or not
     * their response was fetched. Archived sessions are kept for a resume.
     */
    public void deleteSessionsFinishedBefore(Instant cutoff) {
        sessions.values().stream()
                .filter(session -> session.getState().isFinished() && session.getState() != AgentSessionState.ARCHIVED)
                .filter(session -> session.getFinishedAt().filter(cutoff::isAfter).isPresent())
                .map(ClaudeAgentSession::getSessionId)
                .toList()
                .forEach(sessionId -> {
                    LOG.info("[{}] finished before {}, deleting the session", sessionId, cutoff);
                    cleanAllSessionData(sessionId);
                });
    }

    /**
     * Deletes the sessions a restart left behind: folders in the sessions dir of sessions this registry doesn't know
     * and that aren't archived. Nothing can fetch their response or resume them any more. Their chats are not touched.
     *
     * @return the deleted sessions that had an agent folder (so maybe a chat, which is opened after the agent's files
     *         are copied), with that agent
     */
    public Map<UUID, AiAgent> deleteLeftoverSessions() {
        Map<UUID, AiAgent> deleted = new HashMap<>();
        try (Stream<Path> sessionFolderStream = Files.list(sessionFolders.root())) {
            for (Path sessionFolder : sessionFolderStream.filter(Files::isDirectory).toList()) {
                sessionIdOf(sessionFolder).filter(this::isLeftover).ifPresent(sessionId -> {
                    Optional<AiAgent> agent = agentOf(sessionFolder);
                    FileTrees.deleteIfExists(sessionFolder);
                    LOG.info("[{}] left behind by a restart, session data deleted", sessionId);
                    agent.ifPresent(it -> deleted.put(sessionId, it));
                });
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to list " + sessionFolders.root(), e);
        }
        return deleted;
    }

    private boolean isLeftover(UUID sessionId) {
        return !sessions.containsKey(sessionId) && !reservedSessionIds.contains(sessionId) && findArchive(sessionId).isEmpty();
    }

    // the agent folder is the one with the agent's agent.json; a cloned repository may have any name
    private static Optional<AiAgent> agentOf(Path sessionFolder) {
        try (Stream<Path> folders = Files.list(sessionFolder)) {
            return folders.filter(folder -> Files.isRegularFile(folder.resolve(ClaudeAgentConfig.FILE_NAME)))
                    .map(folder -> AiAgent.fromAgentName(folder.getFileName().toString()))
                    .flatMap(Optional::stream)
                    .findFirst();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to list " + sessionFolder, e);
        }
    }

    /**
     * Keeps the session for a later resume: writes the registry's state ({@link ClaudeSessionArchive}) into
     * {@code <session folder>/<agent name>/}, marks the session {@code ARCHIVED} (so automatic cleanups leave its
     * folder alone), and then stops it if it was still running or waiting. Claude Code's own state for the session is
     * already in that folder ({@value #CLAUDE_STATE_FOLDER}). Nothing is copied or deleted.
     *
     * @return the preserved agent folder
     * @throws IllegalStateException when the session is unknown
     */
    public Path archive(UUID sessionId) {
        ClaudeAgentSession session = sessions.get(sessionId);
        if (session == null) {
            throw new IllegalStateException("Claude session " + sessionId + " is unknown");
        }
        Path agentFolder = sessionFolders.agentFolder(sessionId, session.getAgent());
        saveState(session, agentFolder);
        AgentSessionState previous = session.archive();
        if (!previous.isFinished()) {
            LOG.info("[{} {}] archived, stopping", session.getAgent().getAgentName(), sessionId);
            stop(session.process());
        } else {
            LOG.info("[{} {}] archived ({})", session.getAgent().getAgentName(), sessionId, previous);
        }
        return agentFolder;
    }

    /**
     * Continues an archived session: {@code claude -p "<resume prompt>" --resume <session id>} in the archived working
     * directory, with the session's own Claude Code state, the agent's {@code agent.json} and a session slot like any
     * run. The prompt is the agent's resume prompt, or, when the session ended its turn on a call of another agent
     * whose response is stored ({@value #AGENT_CALL_RESPONSE_FILE}), that response. Works from the archive on disk
     * ({@code session-state.json}), so also for a session this registry no longer knows (e.g. after a restart). The
     * new run appends to the session's transcript; the archive file and the stored response are removed, so the
     * session can be archived again.
     *
     * @return the resumed run
     * @throws IllegalStateException       when the session is not archived, or Claude Code can't be started
     * @throws ClaudeSessionLimitException when all concurrent session slots are busy; the session stays archived
     */
    public ClaudeAgentSession resume(UUID sessionId) {
        return resume(sessionId, null);
    }

    // prompt: null for the stored agent response, or else the agent's resume prompt
    private ClaudeAgentSession resume(UUID sessionId, @Nullable String prompt) {
        Path stateFile = findArchive(sessionId)
                .orElseThrow(() -> new IllegalStateException("Claude session " + sessionId + " is not archived"));
        ClaudeSessionArchive archive = readArchive(stateFile);
        Path agentResponseFile = stateFile.resolveSibling(AGENT_CALL_RESPONSE_FILE);
        if (prompt == null && archive.deferredAgentCall() != null && Files.isRegularFile(agentResponseFile)) {
            prompt = agentResponsePrompt(archive.deferredAgentCall(), readAgentResponse(agentResponseFile));
        }
        ClaudeAgentSession archived = sessions.get(sessionId);
        if (archived != null) {
            if (archived.getState() != AgentSessionState.ARCHIVED) {
                throw new IllegalStateException("Claude session " + sessionId + " is " + archived.getState() + ", not archived");
            }
            // the resumed run takes over the session id; a concurrent resume loses this compare-and-remove
            if (!sessions.remove(sessionId, archived)) {
                throw new IllegalStateException("Claude session " + sessionId + " is already being resumed");
            }
        }
        try (SessionSlot slot = reserve(sessionId)) {
            ClaudeAgentSession resumed = slot.startRun(archive.agent(), Path.of(archive.workingDirectory()), true, prompt, null,
                    archive.options());
            deleteAfterResume(stateFile, sessionId);
            deleteAfterResume(agentResponseFile, sessionId);
            return resumed;
        } catch (RuntimeException e) {
            if (archived != null) {
                sessions.putIfAbsent(sessionId, archived);
            }
            throw e;
        }
    }

    /**
     * Records that the session's run ends its turn on {@code question}, unanswered: when the run has ended, the session
     * is archived instead of finished, and {@link #answerDeferredQuestion} resumes it with the answer.
     *
     * @throws IllegalStateException when the session is unknown or no longer running
     */
    public void deferQuestion(UUID sessionId, String question) {
        ClaudeAgentSession session = sessions.get(sessionId);
        if (session == null || session.getState().isFinished()) {
            throw new IllegalStateException("Claude session " + sessionId + " is not running");
        }
        session.deferQuestion(question);
    }

    /** Undoes {@link #deferQuestion}, e.g. when the answer came just in time after all. */
    public void cancelDeferredQuestion(UUID sessionId) {
        Optional.ofNullable(sessions.get(sessionId)).ifPresent(ClaudeAgentSession::cancelDeferredQuestion);
    }

    /**
     * Answers the question a session ended its turn on ({@link #deferQuestion}): resumes the archived session with
     * the answer, or, while its run is still ending the turn, keeps the answer and resumes the session once it is
     * archived. Works from the archive on disk, so also after a restart. When all session slots are busy, the session
     * stays archived and its chat is told to answer again later.
     *
     * @return whether the session was waiting for an answer
     */
    public boolean answerDeferredQuestion(UUID sessionId, String answer) {
        ClaudeAgentSession session = sessions.get(sessionId);
        if (session != null && session.keepEarlyAnswer(answer)) {
            LOG.info("[{} {}] answer received, resuming once the run has ended its turn", session.getAgent().getAgentName(), sessionId);
            return true;
        }
        Optional<String> question = findArchive(sessionId).map(this::readArchive).map(ClaudeSessionArchive::deferredQuestion);
        question.ifPresent(asked -> resumeWithAnswer(sessionId, asked, answer));
        return question.isPresent();
    }

    private void resumeWithAnswer(UUID sessionId, String question, String answer) {
        try {
            resume(sessionId, answerPrompt(question, answer));
        } catch (ClaudeSessionLimitException e) {
            LOG.warn("[{}] the expert answered, but all session slots are busy", sessionId);
            sessionListener.onOutput(sessionId, "All agent sessions are busy, so this one can't continue now: send your answer again later.");
        } catch (IllegalStateException e) {
            LOG.warn("[{}] resuming with the expert's answer failed: {}", sessionId, e.getMessage());
        }
    }

    static String answerPrompt(String question, String answer) {
        return "The expert answered your question \"" + question + "\": \"" + answer + "\". Continue the task and "
                + "return your final answer as before.";
    }

    /**
     * Records that the session's run ends its turn on its call of {@code agent}, whose session
     * {@code calledSessionId} has not responded yet: when the run has ended, the session is archived instead of
     * finished, and {@link #answerDeferredAgentCall} resumes it with the response.
     *
     * @throws IllegalStateException when the session is unknown or no longer running
     */
    public void deferAgentCall(UUID sessionId, AiAgent agent, UUID calledSessionId) {
        ClaudeAgentSession session = sessions.get(sessionId);
        if (session == null || session.getState().isFinished()) {
            throw new IllegalStateException("Claude session " + sessionId + " is not running");
        }
        session.deferAgentCall(new DeferredAgentCall(agent, calledSessionId));
    }

    /** Undoes {@link #deferAgentCall}, e.g. when the response came just in time after all. */
    public void cancelDeferredAgentCall(UUID sessionId) {
        Optional.ofNullable(sessions.get(sessionId)).ifPresent(ClaudeAgentSession::cancelDeferredAgentCall);
    }

    /**
     * Hands the response of the called session {@code calledSessionId} to the session whose run ended its turn on
     * that call ({@link #deferAgentCall}): resumes the archived caller with it, or, while the caller's run is still
     * ending the turn, keeps it and resumes the caller once it is archived. Works from the archives on disk, so also
     * after a restart.
     *
     * @param response the called agent's response, as its tool result would have been
     * @return whether a session was waiting for it
     */
    public boolean answerDeferredAgentCall(UUID calledSessionId, String response) {
        for (ClaudeAgentSession session : sessions.values()) {
            if (session.keepEarlyAgentResponse(calledSessionId, response)) {
                LOG.info("[{} {}] response of session {} received, resuming once the run has ended its turn",
                        session.getAgent().getAgentName(), session.getSessionId(), calledSessionId);
                return true;
            }
        }
        Optional<ClaudeSessionArchive> caller = findArchives().stream()
                .filter(archive -> archive.deferredAgentCall() != null
                        && archive.deferredAgentCall().calledSessionId().equals(calledSessionId))
                .findFirst();
        caller.ifPresent(archive -> resumeWithAgentResponse(archive.sessionId(), archive.deferredAgentCall(), response));
        return caller.isPresent();
    }

    // stored first: when all slots are busy, the session is resumed with it once one is free
    private void resumeWithAgentResponse(UUID sessionId, DeferredAgentCall call, String response) {
        String agentName = call.agent().getAgentName();
        try {
            storeAgentResponse(sessionId, response);
            resume(sessionId);
        } catch (ClaudeSessionLimitException e) {
            LOG.info("[{}] the {} agent responded, but all session slots are busy: resuming once one is free",
                    sessionId, agentName);
            sessionListener.onOutput(sessionId, "All agent sessions are busy: this one continues with the " + agentName
                    + " agent's response as soon as one is free.");
        } catch (IllegalStateException | UncheckedIOException e) {
            LOG.warn("[{}] resuming with the {} agent's response failed: {}", sessionId, agentName, e.getMessage());
        }
    }

    /**
     * Resumes the archived sessions whose agent call response is stored ({@value #AGENT_CALL_RESPONSE_FILE}) while
     * session slots are free: they came when all were busy. Runs when a run has freed its slot, and at startup.
     */
    void resumeWithStoredAgentResponses() {
        List<ClaudeSessionArchive> archives;
        try {
            archives = findArchives();
        } catch (UncheckedIOException e) {
            LOG.warn("Looking for sessions to resume with a stored agent response failed: {}", e.getMessage());
            return;
        }
        for (ClaudeSessionArchive archive : archives) {
            if (archive.deferredAgentCall() == null || !hasStoredAgentResponse(archive.sessionId())) {
                continue;
            }
            try {
                resume(archive.sessionId());
                LOG.info("[{} {}] resumed with the stored response of the {} agent", archive.agent().getAgentName(),
                        archive.sessionId(), archive.deferredAgentCall().agent().getAgentName());
            } catch (ClaudeSessionLimitException e) {
                return;
            } catch (RuntimeException e) {
                LOG.warn("[{}] resuming with the stored agent response failed: {}", archive.sessionId(), e.getMessage());
            }
        }
    }

    @Override
    public void onApplicationEvent(@NonNull StartupEvent event) {
        resumeWithStoredAgentResponses();
    }

    private void storeAgentResponse(UUID sessionId, String response) {
        Path stateFile = findArchive(sessionId)
                .orElseThrow(() -> new IllegalStateException("Claude session " + sessionId + " is not archived"));
        try {
            Files.writeString(stateFile.resolveSibling(AGENT_CALL_RESPONSE_FILE), response, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store the agent response of session " + sessionId, e);
        }
    }

    private boolean hasStoredAgentResponse(UUID sessionId) {
        return findArchive(sessionId)
                .map(stateFile -> stateFile.resolveSibling(AGENT_CALL_RESPONSE_FILE))
                .filter(Files::isRegularFile)
                .isPresent();
    }

    private static String readAgentResponse(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Unreadable stored agent response " + file, e);
        }
    }

    static String agentResponsePrompt(DeferredAgentCall call, String response) {
        return "The " + call.agent().getAgentName() + " agent you called responded: " + response
                + "\nContinue the task and return your final answer as before.";
    }

    /**
     * The archives of all archived sessions on disk ({@code session-state.json}), also those this registry does not
     * know, e.g. after a restart. An unreadable archive is skipped with a warning.
     */
    public List<ClaudeSessionArchive> findArchives() {
        List<ClaudeSessionArchive> archives = new ArrayList<>();
        try (Stream<Path> sessionFolderStream = Files.list(sessionFolders.root())) {
            for (Path sessionFolder : sessionFolderStream.filter(Files::isDirectory).toList()) {
                sessionIdOf(sessionFolder).flatMap(this::findArchive).ifPresent(stateFile -> {
                    try {
                        archives.add(readArchive(stateFile));
                    } catch (IllegalStateException e) {
                        LOG.warn("Skipping {}: {}", stateFile, e.getMessage());
                    }
                });
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to list " + sessionFolders.root(), e);
        }
        return archives;
    }

    // empty for folders in the sessions dir that are not named after a session id
    private static Optional<UUID> sessionIdOf(Path sessionFolder) {
        try {
            return Optional.of(UUID.fromString(sessionFolder.getFileName().toString()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    // session-state.json in one of the session's agent folders
    private Optional<Path> findArchive(UUID sessionId) {
        Path sessionFolder = sessionFolders.of(sessionId);
        if (!Files.isDirectory(sessionFolder)) {
            return Optional.empty();
        }
        try (Stream<Path> agentFolders = Files.list(sessionFolder)) {
            return agentFolders.map(folder -> folder.resolve(ClaudeSessionArchive.STATE_FILE))
                    .filter(Files::isRegularFile)
                    .findFirst();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to list " + sessionFolder, e);
        }
    }

    private ClaudeSessionArchive readArchive(Path stateFile) {
        try {
            return JsonUtility.fromJson(Files.readAllBytes(stateFile), ClaudeSessionArchive.class);
        } catch (IOException | SerializationException e) {
            throw new IllegalStateException("Unreadable archive " + stateFile, e);
        }
    }

    private static void deleteAfterResume(Path file, UUID sessionId) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            LOG.warn("[{}] removing {} after resume failed", sessionId, file, e);
        }
    }

    private void saveState(ClaudeAgentSession session, Path agentFolder) {
        ClaudeSessionArchive archive = new ClaudeSessionArchive(session.getSessionId(), session.getAgent(),
                session.getWorkingDirectory().toString(), session.getState(), session.getStartedAt(),
                session.getLastOutputAt(), Instant.now(), session.resultText().orElse(null), session.isVerboseOutput(),
                session.getOptions().actorAccountId(), session.deferredQuestion().orElse(null),
                session.deferredAgentCall().orElse(null), session.getOptions().attributes());
        try {
            Files.write(agentFolder.resolve(ClaudeSessionArchive.STATE_FILE), JsonUtility.toJsonAsBytes(archive));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to save the state of session " + session.getSessionId(), e);
        }
    }

    @PreDestroy
    void killAllSessions() {
        sessions.keySet().forEach(this::killSession);
    }

    /**
     * Whether Claude Code gets agent-proxy's environment variable {@code name}: an allowlist, so agent-proxy's own
     * secrets (SOURCES_REPO_ACCESS_TOKEN, PUSHCA_API_KEY, any added later) never reach the run, where anything the
     * agent runs could read them. It gets the model provider's settings ({@code ANTHROPIC_*}, the API key among them:
     * Claude Code can't work without it), its own settings ({@code CLAUDE_CODE_*}) and a few basics.
     */
    static boolean isInheritedByClaudeCode(String name) {
        return INHERITED_ENVIRONMENT.contains(name) || INHERITED_ENVIRONMENT_PREFIXES.stream().anyMatch(name::startsWith);
    }

    private static Path createFolder(UUID sessionId, Path folder) {
        try {
            return Files.createDirectories(folder);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create the folder " + folder + " of session " + sessionId, e);
        }
    }

    private static void requirePrepared(UUID sessionId, Path... folders) {
        for (Path folder : folders) {
            if (!Files.isDirectory(folder)) {
                throw new IllegalStateException("Session " + sessionId + " is not prepared: " + folder + " does not exist");
            }
        }
    }

    // a new run creates Claude Code's session under our id; a resumed run continues it
    private List<String> command(ClaudeAgentConfig config, ClaudeAgentSession session, Path mavenFolder,
                                 @Nullable String prompt, @Nullable String promptContext) {
        boolean resume = session.isResumed();
        String sessionId = session.getSessionId().toString();
        List<String> command = new ArrayList<>(List.of(
                claudeExecutable, "-p", prompt != null ? prompt : resume ? config.resumePrompt() : kickOffPrompt(config, promptContext),
                resume ? "--resume" : "--session-id", sessionId,
                "--output-format", "stream-json", "--verbose",
                // nobody can answer Claude Code's built-in question tool in a headless run: ask_expert does it instead
                "--disallowedTools", "AskUserQuestion",
                "--mcp-config", mcpServer.mcpConfig(session.getSessionId(), session.mcpToken())));
        if (config.model() != null) {
            command.addAll(List.of("--model", config.model()));
        }
        if (config.effort() != null) {
            command.addAll(List.of("--effort", config.effort()));
        }
        if (config.responseSchema() != null) {
            command.addAll(List.of("--json-schema", responseSchema(config)));
        }
        command.addAll(List.of("--settings", settings(config, mavenFolder)));
        // headless runs can only call pre-allowed MCP tools
        command.add("--allowedTools");
        command.addAll(config.allowedTools());
        command.addAll(mcpServer.allowedTools());
        return command;
    }

    private static String responseSchema(ClaudeAgentConfig config) {
        return JsonUtility.toJson(config.responseSchema());
    }

    // every run: the file tools (and sandboxed Bash) read nothing outside the working directory, so a session can't
    // read another session's folder; plus the agent's Bash sandbox, if it has one
    private static String settings(ClaudeAgentConfig config, Path mavenFolder) {
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("permissions", Map.of("blockReadsOutsideWorkingDirectories", true));
        if (config.sandbox() != null) {
            settings.put("sandbox", sandbox(config.sandbox(), mavenFolder));
        }
        return JsonUtility.toJson(settings);
    }

    // the agent's sandbox, plus writes to the run's Maven folder: it is outside the working directory
    private static JsonNode sandbox(JsonNode configured, Path mavenFolder) {
        if (!(configured.deepCopy() instanceof ObjectNode sandbox)) {
            throw new IllegalStateException("sandbox in " + ClaudeAgentConfig.FILE_NAME + " is not an object");
        }
        sandbox.withObjectProperty("filesystem").withArrayProperty("allowWrite").add(mavenFolder.toString());
        return sandbox;
    }

    // the agent's kick-off prompt, then the session's context, then the response schema at the very end
    private String kickOffPrompt(ClaudeAgentConfig config, @Nullable String promptContext) {
        StringBuilder prompt = new StringBuilder(config.kickOffPrompt());
        if (promptContext != null) {
            prompt.append("\n\n").append(promptContext);
        }
        if (config.responseSchema() != null) {
            prompt.append("\n\nReturn your final answer as JSON matching this schema:\n").append(responseSchema(config));
        }
        return prompt.toString();
    }

    private ClaudeAgentConfig readConfig(AiAgent agent, Path agentFolder) {
        Path configFile = agentFolder.resolve(ClaudeAgentConfig.FILE_NAME);
        try {
            ClaudeAgentConfig config = JsonUtility.fromJson(Files.readAllBytes(configFile), ClaudeAgentConfig.class);
            if (config == null || config.kickOffPrompt() == null || config.kickOffPrompt().isBlank()) {
                throw new IllegalStateException(configFile + " has no kickOffPrompt");
            }
            return config;
        } catch (IOException | SerializationException e) {
            throw new IllegalStateException("Agent " + agent.getAgentName() + " has no readable " + configFile, e);
        }
    }


    // reading continuously also keeps Claude Code from stalling on a full output pipe
    private void streamOutput(ClaudeAgentSession session) {
        try (BufferedReader output = session.process().inputReader(StandardCharsets.UTF_8);
             // a resumed run continues the transcript of the archived one
             BufferedWriter transcript = Files.newBufferedWriter(session.getTranscriptFile(), StandardCharsets.UTF_8,
                     StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                     session.isResumed() ? StandardOpenOption.APPEND : StandardOpenOption.TRUNCATE_EXISTING)) {
            for (String line; (line = output.readLine()) != null; ) {
                transcript.write(line);
                transcript.newLine();
                transcript.flush();
                session.recordOutput();
                handleOutputLine(session, line);
            }
        } catch (IOException | RuntimeException e) {
            reportReadFailure(session, e);
        } finally {
            onSessionFinishedHandler(session.getSessionId());
        }
    }

    // stopping a session (archive, kill) closes its output, possibly under the reading thread: the expected end
    private static void reportReadFailure(ClaudeAgentSession session, Exception e) {
        AgentSessionState state = session.getState();
        if (state == AgentSessionState.ARCHIVED || state == AgentSessionState.KILLED) {
            LOG.debug("[{} {}] output closed on stop ({})", session.getAgent().getAgentName(), session.getSessionId(), state, e);
        } else {
            LOG.error("[{} {}] reading Claude Code output failed", session.getAgent().getAgentName(), session.getSessionId(), e);
        }
    }

    private void handleOutputLine(ClaudeAgentSession session, String line) {
        Optional<JsonNode> parsed = parseEvent(line);
        if (parsed.isEmpty()) {
            // not an event: stderr is merged into the same stream (warnings, errors)
            reportOutput(session, ClaudeStreamEvents.truncate(line), false);
            return;
        }
        JsonNode event = parsed.get();
        ClaudeStreamEvents.summarize(event)
                .ifPresent(summary -> reportOutput(session, summary, ClaudeStreamEvents.isVerbose(event)));
        if (ClaudeStreamEvents.isResult(event)) {
            session.recordResult(ClaudeStreamEvents.resultText(event).orElse(null),
                    ClaudeStreamEvents.structuredOutput(event).orElse(null), ClaudeStreamEvents.isSuccessfulResult(event));
        }
        if (session.isDeferred()
                && ClaudeStreamEvents.hasToolResultStartingWith(event, AgentProxyMcpServer.NO_ANSWER_YET)) {
            archiveOnNoAnswer(session);
        }
    }

    // the agent got "no answer yet": stopped right away, so it can't act on it (e.g. take it for a "no")
    private void archiveOnNoAnswer(ClaudeAgentSession session) {
        LOG.info("[{} {}] no answer yet, archiving until it comes: {}", session.getAgent().getAgentName(),
                session.getSessionId(), session.describeDeferral());
        try {
            archive(session.getSessionId());
        } catch (RuntimeException e) {
            LOG.error("[{} {}] archiving until the answer comes failed", session.getAgent().getAgentName(),
                    session.getSessionId(), e);
        }
    }

    // verbose output (thinking) is shown only by sessions started with verbose output; otherwise it is logged at
    // DEBUG and not reported. Thinking-token progress arrives every few tokens as the same line ("system:
    // thinking_tokens"): verbose output that repeats the previous line is dropped, so a run of it shows once.
    private void reportOutput(ClaudeAgentSession session, String output, boolean verbose) {
        if (session.reportOutput(output) && verbose) {
            return;
        }
        if (verbose && !session.isVerboseOutput()) {
            LOG.debug("[{} {}] {}", session.getAgent().getAgentName(), session.getSessionId(), output);
            return;
        }
        LOG.info("[{} {}] {}", session.getAgent().getAgentName(), session.getSessionId(), output);
        sessionListener.onOutput(session.getSessionId(), output);
    }

    // empty for lines that are not JSON, and for blank or "null" lines, which parse to no value
    private static Optional<JsonNode> parseEvent(String line) {
        try {
            return Optional.ofNullable(JsonUtility.fromJson(line, JsonNode.class))
                    .filter(event -> !event.isNull() && !event.isMissingNode());
        } catch (SerializationException e) {
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
     *       A {@code WAITING} session silent for that long (counted from when it started waiting) is
     *       {@link #archive archived} instead: stopped, but kept for a later {@link #resume}.</li>
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
            if (now.isAfter(session.getLastOutputAt().plus(idleTimeout))) {
                if (session.transition(AgentSessionState.RUNNING, AgentSessionState.TIMED_OUT)) {
                    LOG.warn("[{} {}] no output for {}, stopping and deleting the session", agentName, sessionId, idleTimeout);
                    stop(session.process());
                    cleanAllSessionData(sessionId);
                    return;
                }
                if (session.getState() == AgentSessionState.WAITING) {
                    archiveIdleWaitingSession(session);
                    return;
                }
            }
        }
    }

    private void archiveIdleWaitingSession(ClaudeAgentSession session) {
        String agentName = session.getAgent().getAgentName();
        UUID sessionId = session.getSessionId();
        LOG.warn("[{} {}] waiting without output for {}, archiving the session", agentName, sessionId, idleTimeout);
        try {
            archive(sessionId);
        } catch (RuntimeException e) {
            // the state could not be saved: stop the run anyway, keeping its files
            LOG.error("[{} {}] archiving failed, stopping the session", agentName, sessionId, e);
            if (session.finish(AgentSessionState.TIMED_OUT)) {
                stop(session.process());
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
        // archived when "no answer yet" showed up, or now if the run ended its turn before that
        boolean awaitsAnswer = session.isDeferred()
                && (session.getState() == AgentSessionState.ARCHIVED
                    || !session.getState().isFinished() && archiveUntilAnswered(session));
        if (!awaitsAnswer) {
            Optional<AgentContext> response = response(session);
            boolean succeeded = exitCode == 0 && session.isResultSuccessful() && response.isPresent();
            session.finish(succeeded ? AgentSessionState.DONE : AgentSessionState.FAILED);
            response.ifPresent(context -> {
                boolean written = writeResponse(session, context);
                sessionListener.onResponse(sessionId, answersPerSchema(session)
                        ? JsonUtility.toJson(context) : session.resultText().orElseThrow());
                if (written) {
                    responseReadyPublisher.publishEvent(new AgentResponseReady(session.getAgent(), sessionId));
                }
            });
        }
        sessionSlots.release();
        // an answer that came while the run was ending its turn; from here on answers find the archive
        Optional<String> earlyAnswer = awaitsAnswer ? session.archivedForAnswer() : Optional.empty();
        Optional<String> earlyAgentResponse = awaitsAnswer ? session.earlyAgentResponse() : Optional.empty();
        session.markFinished();
        LOG.info("[{} {}] finished: {}, exit code {}", session.getAgent().getAgentName(), sessionId, session.getState(), exitCode);
        earlyAnswer.ifPresent(answer -> resumeWithAnswer(sessionId, session.deferredQuestion().orElseThrow(), answer));
        earlyAgentResponse.ifPresent(response ->
                resumeWithAgentResponse(sessionId, session.deferredAgentCall().orElseThrow(), response));
        // the freed slot: for a session whose agent call response came while all slots were busy
        resumeWithStoredAgentResponses();
    }

    // the run ended its turn on a deferred question or agent call: archived (no response written) until the answer
    private boolean archiveUntilAnswered(ClaudeAgentSession session) {
        try {
            saveState(session, sessionFolders.agentFolder(session.getSessionId(), session.getAgent()));
        } catch (RuntimeException e) {
            LOG.error("[{} {}] archiving until the answer comes failed", session.getAgent().getAgentName(),
                    session.getSessionId(), e);
            return false;
        }
        session.archive();
        LOG.info("[{} {}] archived until the answer comes: {}", session.getAgent().getAgentName(), session.getSessionId(),
                session.describeDeferral());
        return true;
    }

    // the final answer as an agent context: the structured output, or the text as one item; empty when the run ended
    // without an answer or with an invalid one
    private Optional<AgentContext> response(ClaudeAgentSession session) {
        if (session.resultText().isEmpty() && session.structuredOutput().isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(answersPerSchema(session)
                    ? structuredResponse(session)
                    : new AgentContext(List.of(new StringAgentContextItem(ClaudeAgentConfig.DEFAULT_RESPONSE_ITEM,
                            session.resultText().orElse(null)))));
        } catch (RuntimeException e) {
            LOG.error("[{} {}] invalid final answer: {}", session.getAgent().getAgentName(), session.getSessionId(), e.getMessage());
            sessionListener.onOutput(session.getSessionId(), "The agent's final answer is invalid: " + e.getMessage());
            return Optional.empty();
        }
    }

    // per the response schema, unless the run ended with an error (max turns, API error, ...) and no structured
    // output: then its error text is kept, as for an agent without a schema
    private static boolean answersPerSchema(ClaudeAgentSession session) {
        return session.isStructuredResponse() && (session.isResultSuccessful() || session.structuredOutput().isPresent());
    }

    private static AgentContext structuredResponse(ClaudeAgentSession session) {
        JsonNode output = session.structuredOutput().orElseThrow(() -> new IllegalArgumentException("no structured output"));
        return Objects.requireNonNull(JsonUtility.getSimpleMapper().treeToValue(output, AgentContext.class), "empty structured output");
    }

    // written to a temporary file and renamed, so a reader never sees a half-written response
    private boolean writeResponse(ClaudeAgentSession session, AgentContext response) {
        Path responseFile = session.getResponseFile();
        Path partial = responseFile.resolveSibling(responseFile.getFileName() + ".partial");
        try {
            Files.write(partial, JsonUtility.toJsonAsBytes(response));
            Files.move(partial, responseFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            LOG.error("[{} {}] writing {} failed", session.getAgent().getAgentName(), session.getSessionId(), session.getResponseFile(), e);
            return false;
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
}
