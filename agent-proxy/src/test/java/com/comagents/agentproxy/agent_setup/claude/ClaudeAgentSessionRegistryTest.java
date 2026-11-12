package com.comagents.agentproxy.agent_setup.claude;

import com.comagents.agentproxy.domain.AgentSessionState;
import com.comagents.agentproxy.domain.AiAgent;
import com.comagents.agentproxy.workspace.SessionFolders;
import io.micronaut.serde.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runs the registry against a fake {@code claude} script, so no test calls the real Claude API. */
@SuppressWarnings("resource")
class ClaudeAgentSessionRegistryTest {

    private static final Duration WAIT = Duration.ofSeconds(20);

    private static final String KICK_OFF_PROMPT = "Review the project in the current directory.";

    private static final String SUCCESSFUL_RUN = """
            printf '%s\\n' "$@" > "$(dirname "$0")/args.txt"
            printf '%s' "$CLAUDE_CONFIG_DIR" > "$(dirname "$0")/config-dir.txt"
            # stdin must be empty, not an open pipe: this would block otherwise
            cat > "$(dirname "$0")/stdin.txt"
            # printf, not echo: sh's echo would turn the \\n escapes inside the JSON into real line breaks
            printf '%s\\n' '{"type":"system","subtype":"init","model":"test-model","tools":["Read"],"mcp_servers":[]}'
            printf '%s\\n' '{"type":"assistant","message":{"content":[{"type":"tool_use","name":"Read","input":{"file_path":"src/Main.java"}}]}}'
            printf '%s\\n' 'a warning on stderr' >&2
            printf '\\n'
            printf '%s\\n' 'null'
            printf '%s\\n' '{"type":"result","subtype":"success","is_error":false,"num_turns":2,"result":"# Report\\n\\nAll good"}'
            """;

    // first run: works until archived; resumed run (--resume): finishes with an answer
    private static final String INTERRUPTED_THEN_RESUMED = """
            printf '%s\\n' "$@" > "$(dirname "$0")/args.txt"
            printf '%s' "$CLAUDE_CODE_RESUME_INTERRUPTED_TURN" > "$(dirname "$0")/resume-env.txt"
            case " $* " in
              *" --resume "*) printf '%s\\n' '{"type":"result","subtype":"success","is_error":false,"result":"resumed answer"}' ;;
              *) printf '%s\\n' 'first run'; exec sleep 60 ;;
            esac
            """;

    @TempDir
    Path scripts;

    @TempDir
    Path workingDirectory;

    @TempDir
    Path sessionsRoot;

    private final List<UUID> sessionIds = new ArrayList<>();
    private SessionFolders sessionFolders;
    private ClaudeAgentSessionRegistry registry;

    @BeforeEach
    void createSessionFolders() {
        sessionFolders = new SessionFolders(sessionsRoot.toString());
    }

    @AfterEach
    void cleanUp() {
        sessionIds.forEach(registry::cleanAllSessionData);
    }

    @Test
    void runsKickOffPromptAndWritesResponse() throws Exception {
        registry = registry(fakeClaude(SUCCESSFUL_RUN), 2, WAIT);
        UUID sessionId = newSessionId();

        ClaudeAgentSession session = registry.createSession(sessionId, AiAgent.CODE_REVIEW, workingDirectory);

        assertEquals(Optional.of(AgentSessionState.DONE), session.awaitFinished(WAIT));
        assertEquals(workingDirectory, session.getWorkingDirectory());
        assertEquals(sessionFolders.agentFolder(sessionId, AiAgent.CODE_REVIEW).resolve("claude-state").toString(),
                Files.readString(scripts.resolve("config-dir.txt")), "Claude Code's state is kept per session");
        assertEquals(sessionFolders.of(sessionId).resolve("code_review/agent-response-" + sessionId + ".md"), session.getResponseFile());
        assertEquals("# Report\n\nAll good", Files.readString(session.getResponseFile()));
        assertEquals(6, Files.readAllLines(session.getTranscriptFile()).size());
        List<String> args = Files.readAllLines(scripts.resolve("args.txt"));
        assertEquals(List.of("-p", KICK_OFF_PROMPT), args.subList(0, 2));
        assertTrue(args.containsAll(List.of("--session-id", sessionId.toString(), "--output-format", "stream-json", "--verbose")));
        assertEquals("test-model", args.get(args.indexOf("--model") + 1));
        assertEquals("high", args.get(args.indexOf("--effort") + 1));
        assertFalse(args.contains("--allowedTools"), "agent.json lists no allowed tools");
        assertEquals("", Files.readString(scripts.resolve("stdin.txt")));
    }

    @Test
    void passesAllowedToolsFromAgentConfig() throws Exception {
        registry = registry(fakeClaude(SUCCESSFUL_RUN), 2, WAIT);
        UUID sessionId = newSessionId();
        writeAgentConfig(sessionId, """
                {"allowedTools": ["Skill", "WebFetch(domain:osv.dev)"], "kickOffPrompt": "Review it"}""");

        registry.createSession(sessionId, AiAgent.CODE_REVIEW, workingDirectory).awaitFinished(WAIT);

        List<String> args = Files.readAllLines(scripts.resolve("args.txt"));
        int allowedTools = args.indexOf("--allowedTools");
        assertEquals(List.of("Skill", "WebFetch(domain:osv.dev)"), args.subList(allowedTools + 1, allowedTools + 3));
    }

    @Test
    void ignoresAllowRulesInProjectSettings() throws Exception {
        Files.createDirectories(workingDirectory.resolve(".claude"));
        Files.writeString(workingDirectory.resolve(".claude/settings.json"), """
                {"permissions": {"allow": ["Bash(rm *)"], "deny": ["Edit"]}}""");
        registry = registry(fakeClaude(SUCCESSFUL_RUN), 2, WAIT);

        registry.createSession(newSessionId(), AiAgent.CODE_REVIEW, workingDirectory).awaitFinished(WAIT);

        List<String> args = Files.readAllLines(scripts.resolve("args.txt"));
        assertFalse(args.contains("--allowedTools"), "only agent.json grants tools");
        assertFalse(args.contains("Bash(rm *)"));
    }

    @Test
    void omitsModelAndEffortWhenNotConfigured() throws Exception {
        registry = registry(fakeClaude(SUCCESSFUL_RUN), 2, WAIT);
        UUID sessionId = newSessionId();
        writeAgentConfig(sessionId, """
                {"kickOffPrompt": "Review it"}""");

        registry.createSession(sessionId, AiAgent.CODE_REVIEW, workingDirectory).awaitFinished(WAIT);

        List<String> args = Files.readAllLines(scripts.resolve("args.txt"));
        assertFalse(args.contains("--model"));
        assertFalse(args.contains("--effort"));
    }

    @Test
    void failsWithoutSuccessfulResult() throws Exception {
        registry = registry(fakeClaude("echo 'boom' >&2; exit 1"), 2, WAIT);

        ClaudeAgentSession session = registry.createSession(newSessionId(), AiAgent.CODE_REVIEW, workingDirectory);

        assertEquals(Optional.of(AgentSessionState.FAILED), session.awaitFinished(WAIT));
        assertFalse(Files.exists(session.getResponseFile()));
    }

    @Test
    void killsRunningSession() throws Exception {
        registry = registry(fakeClaude("exec sleep 60"), 2, WAIT.multipliedBy(10));
        UUID sessionId = newSessionId();
        ClaudeAgentSession session = registry.createSession(sessionId, AiAgent.CODE_REVIEW, workingDirectory);

        registry.killSession(sessionId);

        assertEquals(Optional.of(AgentSessionState.KILLED), session.awaitFinished(WAIT));
    }

    @Test
    void stopsSessionAfterTimeout() throws Exception {
        registry = registry(fakeClaude("exec sleep 60"), 2, Duration.ofMillis(500));

        ClaudeAgentSession session = registry.createSession(newSessionId(), AiAgent.CODE_REVIEW, workingDirectory);

        assertEquals(Optional.of(AgentSessionState.TIMED_OUT), session.awaitFinished(WAIT));
    }

    @Test
    void silentSessionIsStoppedAndDeleted() throws Exception {
        registry = registry(fakeClaude("exec sleep 60"), 2, WAIT.multipliedBy(10), Duration.ofMillis(500));
        UUID sessionId = newSessionId();

        ClaudeAgentSession session = registry.createSession(sessionId, AiAgent.CODE_REVIEW, workingDirectory);

        assertEquals(Optional.of(AgentSessionState.TIMED_OUT), session.awaitFinished(WAIT));
        await("idle session forgotten and its folder deleted").atMost(WAIT)
                .until(() -> registry.findSession(sessionId).isEmpty() && !Files.exists(sessionFolders.of(sessionId)));
    }

    @Test
    void outputKeepsSessionAlive() throws Exception {
        registry = registry(fakeClaude("""
                for i in 1 2 3 4 5 6 7 8 9 10; do printf '%s\\n' "still working $i"; sleep 0.2; done
                printf '%s\\n' '{"type":"result","subtype":"success","is_error":false,"result":"done"}'
                """), 2, WAIT, Duration.ofSeconds(1));
        UUID sessionId = newSessionId();

        ClaudeAgentSession session = registry.createSession(sessionId, AiAgent.CODE_REVIEW, workingDirectory);

        // 2 s of output, never 1 s apart: not idle, although longer than the idle timeout
        assertEquals(Optional.of(AgentSessionState.DONE), session.awaitFinished(WAIT));
        assertTrue(Files.exists(session.getResponseFile()));
    }

    @Test
    void silentWaitingSessionIsArchivedNotDeleted() throws Exception {
        registry = registry(fakeClaude("exec sleep 60"), 2, WAIT.multipliedBy(10), Duration.ofMillis(500));
        UUID sessionId = newSessionId();
        ClaudeAgentSession session = registry.createSession(sessionId, AiAgent.CODE_REVIEW, workingDirectory);

        try (ClaudeAgentSessionRegistry.Waiting _ = registry.markWaiting(sessionId)) {
            assertEquals(Optional.of(AgentSessionState.ARCHIVED), session.awaitFinished(WAIT));
        }

        assertEquals(AgentSessionState.ARCHIVED, session.getState(), "ending the wait does not revive it");
        assertTrue(registry.findSession(sessionId).isPresent(), "kept, not deleted");
        assertTrue(Files.isDirectory(workingDirectory));
        ClaudeSessionArchive state = readArchive(sessionFolders.agentFolder(sessionId, AiAgent.CODE_REVIEW));
        assertEquals(AgentSessionState.WAITING, state.stateBeforeArchive());
    }

    @Test
    void idleTimeoutOfWaitCountsFromItsStart() throws Exception {
        registry = registry(fakeClaude("exec sleep 60"), 2, WAIT.multipliedBy(10), Duration.ofSeconds(2));
        UUID sessionId = newSessionId();
        ClaudeAgentSession session = registry.createSession(sessionId, AiAgent.CODE_REVIEW, workingDirectory);
        Thread.sleep(1500);    // silent, but not yet idle

        try (ClaudeAgentSessionRegistry.Waiting _ = registry.markWaiting(sessionId)) {
            Thread.sleep(1500);    // 3 s since the last output, only 1.5 s since the wait started
            assertEquals(AgentSessionState.WAITING, session.getState());
            assertEquals(Optional.of(AgentSessionState.ARCHIVED), session.awaitFinished(WAIT));
        }
    }

    @Test
    void overlappingWaitsKeepSessionWaitingUntilTheLastEnds() throws Exception {
        registry = registry(fakeClaude("exec sleep 60"), 2, WAIT.multipliedBy(10));
        UUID sessionId = newSessionId();
        ClaudeAgentSession session = registry.createSession(sessionId, AiAgent.CODE_REVIEW, workingDirectory);

        try (ClaudeAgentSessionRegistry.Waiting _ = registry.markWaiting(sessionId)) {
            try (ClaudeAgentSessionRegistry.Waiting _ = registry.markWaiting(sessionId)) {
                assertEquals(AgentSessionState.WAITING, session.getState());
            }
            assertEquals(AgentSessionState.WAITING, session.getState(), "the outer wait is still open");
        }
        assertEquals(AgentSessionState.RUNNING, session.getState());
        registry.killSession(sessionId);
    }

    @Test
    void waitingSessionCanBeKilled() throws Exception {
        registry = registry(fakeClaude("exec sleep 60"), 2, WAIT.multipliedBy(10));
        UUID sessionId = newSessionId();
        ClaudeAgentSession session = registry.createSession(sessionId, AiAgent.CODE_REVIEW, workingDirectory);

        try (ClaudeAgentSessionRegistry.Waiting _ = registry.markWaiting(sessionId)) {
            registry.killSession(sessionId);
            assertEquals(Optional.of(AgentSessionState.KILLED), session.awaitFinished(WAIT));
        }
        assertEquals(AgentSessionState.KILLED, session.getState(), "ending the wait does not revive a killed session");
    }

    @Test
    void sessionThatExitsWhileWaitingStillFinishes() throws Exception {
        registry = registry(fakeClaude(SUCCESSFUL_RUN.replace("printf '%s\\n' '{\"type\":\"result\"", "sleep 1; printf '%s\\n' '{\"type\":\"result\"")), 2, WAIT);
        UUID sessionId = newSessionId();
        ClaudeAgentSession session = registry.createSession(sessionId, AiAgent.CODE_REVIEW, workingDirectory);

        try (ClaudeAgentSessionRegistry.Waiting _ = registry.markWaiting(sessionId)) {
            assertEquals(Optional.of(AgentSessionState.DONE), session.awaitFinished(WAIT));
        }
    }

    @Test
    void cannotWaitOnUnknownOrFinishedSession() throws Exception {
        registry = registry(fakeClaude("exit 1"), 2, WAIT);
        UUID sessionId = newSessionId();
        registry.createSession(sessionId, AiAgent.CODE_REVIEW, workingDirectory).awaitFinished(WAIT);

        assertThrows(IllegalStateException.class, () -> registry.markWaiting(sessionId));
        assertThrows(IllegalStateException.class, () -> registry.markWaiting(UUID.randomUUID()));
    }

    @Test
    void rejectsSessionsAboveLimitAndFreesSlotWhenFinished() throws Exception {
        registry = registry(fakeClaude("exec sleep 60"), 1, WAIT.multipliedBy(10));
        UUID first = newSessionId();
        ClaudeAgentSession running = registry.createSession(first, AiAgent.CODE_REVIEW, workingDirectory);

        assertThrows(ClaudeSessionLimitException.class,
                () -> registry.createSession(newSessionId(), AiAgent.CODE_REVIEW, workingDirectory));

        registry.killSession(first);
        running.awaitFinished(WAIT);
        registry.createSession(newSessionId(), AiAgent.CODE_REVIEW, workingDirectory);
    }

    @Test
    void rejectsDuplicateSessionId() throws Exception {
        registry = registry(fakeClaude(SUCCESSFUL_RUN), 2, WAIT);
        UUID sessionId = newSessionId();
        registry.createSession(sessionId, AiAgent.CODE_REVIEW, workingDirectory).awaitFinished(WAIT);

        assertThrows(IllegalStateException.class, () -> registry.createSession(sessionId, AiAgent.CODE_REVIEW, workingDirectory));
    }

    @Test
    void rejectsAgentWithoutKickOffPrompt() throws Exception {
        registry = registry(fakeClaude(SUCCESSFUL_RUN), 2, WAIT);
        UUID sessionId = newSessionId();
        writeAgentConfig(sessionId, """
                {"model": "test-model", "kickOffPrompt": "  "}""");

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> registry.createSession(sessionId, AiAgent.CODE_REVIEW, workingDirectory));
        assertTrue(e.getMessage().contains("kickOffPrompt"));
        assertTrue(registry.findSession(sessionId).isEmpty());
    }

    @Test
    void rejectsAgentWithoutConfigFile() throws Exception {
        registry = registry(fakeClaude(SUCCESSFUL_RUN), 2, WAIT);
        UUID sessionId = newSessionId();
        Files.delete(sessionFolders.agentFolder(sessionId, AiAgent.CODE_REVIEW).resolve(ClaudeAgentConfig.FILE_NAME));

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> registry.createSession(sessionId, AiAgent.CODE_REVIEW, workingDirectory));
        assertTrue(e.getMessage().contains("agent.json"));
    }

    @Test
    void cleanAllSessionDataStopsAndDeletesEverything() throws Exception {
        registry = registry(fakeClaude("exec sleep 60"), 2, WAIT.multipliedBy(10));
        UUID sessionId = newSessionId();
        registry.createSession(sessionId, AiAgent.CODE_REVIEW, workingDirectory);

        registry.cleanAllSessionData(sessionId);

        assertTrue(registry.findSession(sessionId).isEmpty());
        assertFalse(Files.exists(sessionFolders.of(sessionId)));
    }

    @Test
    void reservationRejectsSessionsAboveLimit() throws Exception {
        registry = registry(fakeClaude(SUCCESSFUL_RUN), 1, WAIT);
        try (ClaudeAgentSessionRegistry.SessionSlot _ = registry.reserve(UUID.randomUUID())) {
            assertThrows(ClaudeSessionLimitException.class, () -> registry.reserve(UUID.randomUUID()));
        }

        assertReservable(UUID.randomUUID());
    }

    @Test
    void closingUnstartedSlotFreesSessionId() throws Exception {
        registry = registry(fakeClaude(SUCCESSFUL_RUN), 2, WAIT);
        UUID sessionId = UUID.randomUUID();

        try (ClaudeAgentSessionRegistry.SessionSlot _ = registry.reserve(sessionId)) {
            assertThrows(IllegalStateException.class, () -> registry.reserve(sessionId), "id is claimed while reserved");
        }

        assertReservable(sessionId);
        assertTrue(registry.findSession(sessionId).isEmpty());
    }

    @Test
    void failedStartKeepsSlotReservedUntilClosed() throws Exception {
        registry = registry(fakeClaude(SUCCESSFUL_RUN), 1, WAIT);
        UUID sessionId = UUID.randomUUID();

        try (ClaudeAgentSessionRegistry.SessionSlot slot = registry.reserve(sessionId)) {
            assertThrows(IllegalStateException.class, () -> slot.start(AiAgent.CODE_REVIEW, workingDirectory), "not prepared");
            assertThrows(ClaudeSessionLimitException.class, () -> registry.reserve(UUID.randomUUID()));
        }

        assertReservable(UUID.randomUUID());
    }

    @Test
    void startedSlotIsHeldByTheRunUntilItFinishes() throws Exception {
        registry = registry(fakeClaude("exec sleep 60"), 1, WAIT.multipliedBy(10));
        UUID sessionId = newSessionId();
        ClaudeAgentSession session;
        try (ClaudeAgentSessionRegistry.SessionSlot slot = registry.reserve(sessionId)) {
            session = slot.start(AiAgent.CODE_REVIEW, workingDirectory);
        }

        assertThrows(ClaudeSessionLimitException.class, () -> registry.reserve(UUID.randomUUID()), "closing a started slot keeps it");

        registry.killSession(sessionId);
        session.awaitFinished(WAIT);
        assertReservable(UUID.randomUUID());
    }

    @Test
    void archiveSavesStateStopsSessionAndKeepsFiles() throws Exception {
        registry = registry(fakeClaude("printf '%s\\n' 'working'; exec sleep 60"), 1, WAIT.multipliedBy(10));
        UUID sessionId = newSessionId();
        ClaudeAgentSession session = registry.createSession(sessionId, AiAgent.CODE_REVIEW, workingDirectory);

        Path agentFolder = registry.archive(sessionId);

        assertEquals(sessionFolders.agentFolder(sessionId, AiAgent.CODE_REVIEW), agentFolder);
        assertEquals(Optional.of(AgentSessionState.ARCHIVED), session.awaitFinished(WAIT));
        ClaudeSessionArchive state = readArchive(agentFolder);
        assertEquals(sessionId, state.sessionId());
        assertEquals(AiAgent.CODE_REVIEW, state.agent());
        assertEquals(workingDirectory.toString(), state.workingDirectory());
        assertEquals(AgentSessionState.RUNNING, state.stateBeforeArchive(), "state saved before the stop");
        assertEquals(session.getStartedAt(), state.startedAt());
        assertNotNull(state.archivedAt());

        assertTrue(Files.isDirectory(workingDirectory), "session files are kept");
        assertTrue(Files.exists(agentFolder.resolve(ClaudeAgentConfig.FILE_NAME)));
        assertTrue(registry.findSession(sessionId).isPresent(), "an archived session stays known");
        assertReservable(UUID.randomUUID());
    }

    @Test
    void archiveOfFinishedSessionKeepsItsResult() throws Exception {
        registry = registry(fakeClaude(SUCCESSFUL_RUN), 2, WAIT);
        UUID sessionId = newSessionId();
        ClaudeAgentSession session = registry.createSession(sessionId, AiAgent.CODE_REVIEW, workingDirectory);
        session.awaitFinished(WAIT);

        ClaudeSessionArchive state = readArchive(registry.archive(sessionId));

        assertEquals(AgentSessionState.ARCHIVED, session.getState());
        assertEquals(AgentSessionState.DONE, state.stateBeforeArchive());
        assertEquals("# Report\n\nAll good", state.resultText());
        assertTrue(Files.exists(session.getResponseFile()));
    }

    @Test
    void archivedSessionIgnoresKill() throws Exception {
        registry = registry(fakeClaude(SUCCESSFUL_RUN), 2, WAIT);
        UUID sessionId = newSessionId();
        registry.createSession(sessionId, AiAgent.CODE_REVIEW, workingDirectory).awaitFinished(WAIT);
        registry.archive(sessionId);

        registry.killSession(sessionId);

        assertEquals(AgentSessionState.ARCHIVED, registry.findSession(sessionId).orElseThrow().getState());
    }

    @Test
    void archiveOfUnknownSessionFails() throws Exception {
        registry = registry(fakeClaude(SUCCESSFUL_RUN), 2, WAIT);

        assertThrows(IllegalStateException.class, () -> registry.archive(UUID.randomUUID()));
    }

    @Test
    void cleanAllSessionDataDeletesClaudeState() throws Exception {
        registry = registry(fakeClaude(SUCCESSFUL_RUN), 2, WAIT);
        UUID sessionId = newSessionId();
        registry.createSession(sessionId, AiAgent.CODE_REVIEW, workingDirectory).awaitFinished(WAIT);
        Path claudeState = Files.createDirectories(sessionFolders.agentFolder(sessionId, AiAgent.CODE_REVIEW).resolve("claude-state/projects/p"));
        Files.writeString(claudeState.resolve(sessionId + ".jsonl"), "{}");

        registry.cleanAllSessionData(sessionId);

        assertFalse(Files.exists(sessionFolders.of(sessionId)));
    }

    private static ClaudeSessionArchive readArchive(Path agentFolder) throws IOException {
        return ObjectMapper.getDefault()
                .readValue(Files.readAllBytes(agentFolder.resolve(ClaudeSessionArchive.STATE_FILE)), ClaudeSessionArchive.class);
    }

    @Test
    void resumeContinuesArchivedSession() throws Exception {
        registry = registry(fakeClaude(INTERRUPTED_THEN_RESUMED), 2, WAIT);
        UUID sessionId = newSessionId();
        ClaudeAgentSession first = registry.createSession(sessionId, AiAgent.CODE_REVIEW, workingDirectory);
        await("first run produced output").atMost(WAIT)
                .until(() -> Files.exists(first.getTranscriptFile()) && Files.readString(first.getTranscriptFile()).contains("first run"));
        Path agentFolder = registry.archive(sessionId);

        ClaudeAgentSession resumed = registry.resume(sessionId);

        assertTrue(resumed.isResumed());
        assertEquals(Optional.of(AgentSessionState.DONE), resumed.awaitFinished(WAIT));
        assertEquals(Optional.of(resumed), registry.findSession(sessionId));
        List<String> args = Files.readAllLines(scripts.resolve("args.txt"));
        assertEquals(List.of("-p", ClaudeAgentConfig.DEFAULT_RESUME_PROMPT, "--resume", sessionId.toString()), args.subList(0, 4));
        assertFalse(args.contains("--session-id"));
        assertEquals("test-model", args.get(args.indexOf("--model") + 1), "same agent.json as the first run");
        assertEquals("1", Files.readString(scripts.resolve("resume-env.txt")), "finish the interrupted turn first");
        assertEquals("resumed answer", Files.readString(resumed.getResponseFile()));
        String transcript = Files.readString(resumed.getTranscriptFile());
        assertTrue(transcript.contains("first run") && transcript.contains("resumed answer"), "transcript covers both runs");
        assertFalse(Files.exists(agentFolder.resolve(ClaudeSessionArchive.STATE_FILE)), "no longer archived");
    }

    @Test
    void resumeUsesAgentResumePrompt() throws Exception {
        registry = registry(fakeClaude(INTERRUPTED_THEN_RESUMED), 2, WAIT);
        UUID sessionId = newSessionId();
        writeAgentConfig(sessionId, """
                {"kickOffPrompt": "Review it", "resumePrompt": "Pick up the review where you left it."}""");
        registry.createSession(sessionId, AiAgent.CODE_REVIEW, workingDirectory);
        registry.archive(sessionId);

        registry.resume(sessionId).awaitFinished(WAIT);

        assertEquals("Pick up the review where you left it.", Files.readAllLines(scripts.resolve("args.txt")).get(1));
    }

    @Test
    void resumeWorksFromDiskForSessionUnknownToRegistry() throws Exception {
        registry = registry(fakeClaude(INTERRUPTED_THEN_RESUMED), 2, WAIT);
        UUID sessionId = newSessionId();
        registry.createSession(sessionId, AiAgent.CODE_REVIEW, workingDirectory);
        registry.archive(sessionId);
        // e.g. after an agent-proxy restart: a new registry that never saw the session
        ClaudeAgentSessionRegistry restarted = registry(fakeClaude(INTERRUPTED_THEN_RESUMED), 2, WAIT);

        ClaudeAgentSession resumed = restarted.resume(sessionId);

        assertEquals(Optional.of(AgentSessionState.DONE), resumed.awaitFinished(WAIT));
        assertEquals(workingDirectory, resumed.getWorkingDirectory());
    }

    @Test
    void resumeRejectsSessionThatIsNotArchived() throws Exception {
        registry = registry(fakeClaude(INTERRUPTED_THEN_RESUMED), 2, WAIT);
        UUID sessionId = newSessionId();
        registry.createSession(sessionId, AiAgent.CODE_REVIEW, workingDirectory);

        assertThrows(IllegalStateException.class, () -> registry.resume(sessionId), "running, never archived");
        assertThrows(IllegalStateException.class, () -> registry.resume(UUID.randomUUID()), "unknown");

        // a stale archive file does not make a running session resumable
        Files.writeString(sessionFolders.agentFolder(sessionId, AiAgent.CODE_REVIEW).resolve(ClaudeSessionArchive.STATE_FILE),
                "{\"sessionId\":\"" + sessionId + "\",\"agent\":\"CODE_REVIEW\",\"workingDirectory\":\"" + workingDirectory
                        + "\",\"stateBeforeArchive\":\"RUNNING\",\"startedAt\":\"2026-11-12T10:00:00Z\","
                        + "\"lastOutputAt\":\"2026-11-12T10:00:00Z\",\"archivedAt\":\"2026-11-12T10:00:00Z\"}");
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> registry.resume(sessionId));
        assertTrue(e.getMessage().contains("RUNNING"));
    }

    @Test
    void resumeAboveLimitKeepsSessionArchived() throws Exception {
        registry = registry(fakeClaude(INTERRUPTED_THEN_RESUMED), 1, WAIT);
        UUID archivedId = newSessionId();
        registry.createSession(archivedId, AiAgent.CODE_REVIEW, workingDirectory);
        Path agentFolder = registry.archive(archivedId);
        UUID busyId = newSessionId();
        registry.createSession(busyId, AiAgent.CODE_REVIEW, workingDirectory);

        assertThrows(ClaudeSessionLimitException.class, () -> registry.resume(archivedId));

        assertEquals(AgentSessionState.ARCHIVED, registry.findSession(archivedId).orElseThrow().getState());
        assertTrue(Files.exists(agentFolder.resolve(ClaudeSessionArchive.STATE_FILE)));
        registry.killSession(busyId);
    }

    @Test
    void resumedSessionCanBeArchivedAgain() throws Exception {
        registry = registry(fakeClaude(INTERRUPTED_THEN_RESUMED), 2, WAIT);
        UUID sessionId = newSessionId();
        registry.createSession(sessionId, AiAgent.CODE_REVIEW, workingDirectory);
        registry.archive(sessionId);
        registry.resume(sessionId).awaitFinished(WAIT);

        ClaudeSessionArchive state = readArchive(registry.archive(sessionId));

        assertEquals(AgentSessionState.DONE, state.stateBeforeArchive());
        assertEquals("resumed answer", state.resultText());
    }

    @Test
    void rejectsUnpreparedSession() throws Exception {
        registry = registry(fakeClaude(SUCCESSFUL_RUN), 2, WAIT);
        UUID sessionId = UUID.randomUUID();

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> registry.createSession(sessionId, AiAgent.CODE_REVIEW, workingDirectory));

        assertTrue(e.getMessage().contains("not prepared"));
        assertTrue(registry.findSession(sessionId).isEmpty());
    }

    // what setup does before a session starts: the agent folder exists and holds the agent's agent.json
    private UUID newSessionId() throws IOException {
        UUID sessionId = UUID.randomUUID();
        sessionIds.add(sessionId);
        Files.createDirectories(sessionFolders.agentFolder(sessionId, AiAgent.CODE_REVIEW));
        writeAgentConfig(sessionId, """
                {"model": "test-model", "effort": "high", "kickOffPrompt": "%s"}""".formatted(KICK_OFF_PROMPT));
        return sessionId;
    }

    private void writeAgentConfig(UUID sessionId, String json) throws IOException {
        Files.writeString(sessionFolders.agentFolder(sessionId, AiAgent.CODE_REVIEW).resolve(ClaudeAgentConfig.FILE_NAME), json);
    }

    private Path fakeClaude(String body) throws IOException {
        Path script = scripts.resolve("claude");
        Files.writeString(script, "#!/bin/sh\n" + body + "\n");
        Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwx------"));
        return script;
    }

    // a slot is free (and the id unclaimed): reserving succeeds; the reservation is released right away
    private void assertReservable(UUID sessionId) {
        try (ClaudeAgentSessionRegistry.SessionSlot slot = registry.reserve(sessionId)) {
            assertNotNull(slot);
        }
    }

    // idle timeout well above any test's run time, so only the idle tests below exercise it
    private ClaudeAgentSessionRegistry registry(Path claude, int maxConcurrent, Duration timeout) {
        return registry(claude, maxConcurrent, timeout, Duration.ofMinutes(10));
    }

    private ClaudeAgentSessionRegistry registry(Path claude, int maxConcurrent, Duration timeout, Duration idleTimeout) {
        return new ClaudeAgentSessionRegistry(ObjectMapper.getDefault(), claude.toString(), maxConcurrent, timeout, idleTimeout,
                sessionFolders);
    }
}
