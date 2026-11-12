# Claude Code sessions with remote Q&A

Status: running Claude Code headless (sections 1–2), session status and response, archive and resume are
implemented (`ClaudeAgentSessionRegistry`); Pushca and the Q&A parts (sections 3–6) are design only.

agent-proxy prepares a session folder (`POST /{agentName}/start`: clone repo, copy agent files), then runs
Claude Code headless in it. While Claude works, a human admin follows its output and answers its questions
remotely through Pushca.

Everything below relies on documented Claude Code features; sources are listed at the end. Items marked
**verify** were not fully confirmed in the docs and need a prototype before building on them.

## Goals

1. Run Claude Code headless per session, in the session's working directory, with the agent's kick-off prompt.
2. Stream Claude's progress into agent-proxy logs and to the admin (Pushca).
3. Let Claude ask the admin questions mid-task and continue with the answer: in the same run for a quick answer,
   by resuming the session for a slow one (section 4).
4. Let the admin approve or deny actions the agent's permissions don't already allow.
5. Let the admin (or agent-proxy) send new instructions to an existing session.

## Overview

```
admin ◄──── Pushca ────► HumanChannel (agent-proxy)
                           ▲   ▲            │
            progress events│   │questions,  │ new instructions
                           │   │approvals   ▼
  ClaudeAgentSessionRegistry  MCP server /mcp ◄── tool calls (HTTP): ask_human, approve
                   │    ▲                                │
       starts /    │    │ stream-json (stdout)           │
       resumes     ▼    │                                │
                 claude -p  (child process, cwd = session working dir) ───┘
```

## 1. Running Claude Code

One child process per run, started with `ProcessBuilder` (works in the GraalVM native image). No terminal is
needed: `claude -p` runs without a TTY.

- **Session folders** live in `<sessions-dir>` (`agent-proxy.sessions-dir`, default `/var/lib/agent-proxy/sessions`,
  on a persistent volume).
- **Working directory:** the cloned repo `<sessions-dir>/<sessionId>/<repo>`, or `<sessions-dir>/<sessionId>/<agentName>` when
  nothing was cloned (same rule as `AgentWithClaudeCodeSetupService`).
- **Session id:** reuse our `sessionId` as Claude's session id. `--session-id` accepts any valid UUID, so
  agent-proxy never has to read Claude's id back.
- **Environment:** inherits the container env (`ANTHROPIC_API_KEY`, `DISABLE_AUTOUPDATER=1`), plus:
  - `CLAUDE_CONFIG_DIR=<sessions-dir>/<sessionId>/<agentName>/claude-state`: Claude Code's own state for the session
    (settings, the conversation `--resume` reads, `.claude.json`), deleted with the session, kept by an archive,
    never shared between sessions;
  - `CLAUDE_CODE_RESUME_INTERRUPTED_TURN=1` on resumed runs: finish the interrupted turn first.
- **stdin** is `/dev/null`: with an open pipe, `claude -p` waits 3 s for input on every run.

First run (`POST /{agentName}/start`, after setup). Model, effort, allowed tools and the prompt come from the
agent's `agent.json` in the comagents sources:

```bash
claude -p "<kickOffPrompt>" \
  --session-id <sessionId> \
  --output-format stream-json --verbose \
  --disallowedTools AskUserQuestion \
  [--model <model>] [--effort <effort>] \
  --allowedTools <agent.json allowedTools…> mcp__agentproxy__ask_human \
  --mcp-config '<agent-proxy MCP server, section 4>' \
  --permission-prompt-tool mcp__agentproxy__approve
```

Implemented today: everything except the last three lines (`mcp__agentproxy__ask_human`, `--mcp-config`,
`--permission-prompt-tool`), which belong to sections 4–5.

Resumed runs (`resume` after `archive`, the long path of section 4, section 6) use `--resume <sessionId>` instead
of `--session-id <sessionId>`, with the agent's `resumePrompt` (or the answer prompt of section 4).

### Permissions in headless mode

- In `-p` mode a tool call that needs approval and isn't pre-allowed is **denied**, not left waiting
  (unless `--permission-prompt-tool` handles it, section 5).
- A headless run never shows the workspace trust dialog, and every session starts in a new folder, so the
  folder is never trusted. `permissions.allow` rules (and `permissions.additionalDirectories`) in a project
  `.claude/settings.json` are then ignored; Claude Code warns "Ignoring N permissions.allow entries … this
  workspace has not been trusted". Passing the same file with `--settings` does not help. `deny` rules apply
  immediately.
- So an agent's `.claude/settings.json` holds only `deny` rules, and its allowed tools are in `agent.json`
  (`allowedTools`), which agent-proxy passes as `--allowedTools`. Command-line flags are not subject to the
  trust check. A cloned repository's own `allow` rules are never passed on.
- File access outside the working directory: `--add-dir <sessions-dir>/<sessionId>` per agent if needed.
- Absolute paths in permission rules use a double slash: `Edit(//var/lib/agent-proxy/sessions/<sessionId>/**)`.

### HTTP API

`POST /{agentName}/start` doesn't wait for Claude: a whole-project review can take many minutes.

- `POST /{agentName}/start` sets up the session, starts the run in the background, returns **202 Accepted**. The
  request will carry the initiator's id (the Pushca account allowed to answer the session, section 3).
- `GET /agent-status/{agentName}/{sessionId}`: `state` (`RUNNING`, `WAITING`, `DONE`, `FAILED`, `TIMED_OUT`,
  `KILLED`, `ARCHIVED`), start and finish times, and whether a response is available; 404 for an unknown session.
- `GET /agent-response/{agentName}/{sessionId}`: the agent's final answer (`body` base64, `createdAt`). Without
  one: 404 with the session's `state`, so the caller knows whether to try again (`RUNNING`, `WAITING`). The
  session's files are deleted 5 min after the first successful fetch, except for archived sessions.

### Limits

- **Time limit** per run, `claude.session.timeout` (60 min): the process is stopped, state `TIMED_OUT`, files kept.
- **Idle timeout**, `claude.session.idle-timeout` (10 min without any output): a `RUNNING` session is stopped and
  deleted; a `WAITING` session (silent on purpose) is archived instead, its timer counted from when the wait
  started.
- **Concurrency cap**, `claude.session.max-concurrent` (2): Claude Code's docs recommend 4 GB+ RAM per run. The
  slot is reserved before setup, so a busy service answers **429** before cloning anything.
- **Kubernetes** (`k8s/deployment.yaml`): one replica (sessions live in the pod), 4 GiB memory limit, read-only
  root filesystem; writable are the sessions dir (a `PersistentVolumeClaim`, `strategy: Recreate`) and `/tmp` for
  scratch (`emptyDir`); API key and GitHub token from the `agent-proxy-secrets` Secret.

## 2. Streaming output

`--output-format stream-json --verbose` prints one JSON event per line:

| Event | Contains |
|---|---|
| `system` / `init` | model, tools, MCP servers (`mcp_servers`, `mcp_server_errors`) |
| `assistant` | Claude's text and its tool calls (`tool_use`: name, input) |
| `user` | tool results |
| `result` | final answer (the report), cost, session id |

A virtual thread per run reads stdout line by line (stderr merged with `redirectErrorStream(true)`).
Reading continuously also keeps the process from stalling on a full output buffer. For each line:

1. Append it to `<sessions-dir>/<sessionId>/<agentName>/claude-stream.jsonl`: the full transcript, for debugging.
2. Log a one-line summary with the agent name and session id.
3. Publish the summary to the session's Pushca channel (section 3).
4. On the `result` event, save the final text as the session's result.

Summaries, not raw events: tool results can hold whole source files or build logs, and may contain secrets
the agent read. A summary prints the tool name and its main argument (path or command), truncates long
text, never prints tool results, and passes non-JSON lines (stderr warnings) through unchanged:

```
[code_review 3f2b…] init: model=…, tools=24, mcp=[agentproxy]
[code_review 3f2b…] tool: Read src/main/java/…/OrderService.java
[code_review 3f2b…] tool: Bash ./mvnw -B -q test
[code_review 3f2b…] ask_human: Should generated sources under target/ be reviewed?
[code_review 3f2b…] done: success, 42 turns, $0.87, 6m12s
```

`--include-partial-messages` (token-level chunks) is too noisy for logs and is not used.

## 3. HumanChannel: the Pushca boundary

Nothing outside one class depends on Pushca:

```java
public interface HumanChannel {
    /** Sends a progress event about the session to the admin. */
    void publish(UUID sessionId, AgentEvent event);

    /**
     * Sends a question to the admin. The future completes with the answer whenever it arrives, seconds or days
     * later; section 4 decides whether the asking tool call is still waiting for it.
     */
    CompletableFuture<String> ask(UUID sessionId, UUID questionId, String question);

    /** Asks the admin to allow or deny a tool call. */
    ApprovalDecision approve(UUID sessionId, ApprovalRequest request, Duration deadline);
}
```

- `PushcaHumanChannel` is the production implementation; tests use an in-memory fake.
- One Pushca channel per session, addressed by `sessionId`.

### Why Pushca, not Slack or Telegram

Pushca is self-hosted, with its own chat UI (e.g. `chat-room.html?channelId=<sessionId>&accountId=<account>`):

- **Data stays in-house.** Agents ask about the code they work on, so questions, answers and progress can contain
  code, findings or internal details. With Pushca they never pass through a third-party messaging service.
- **No bot tokens or third-party accounts** that could leak and be used to answer agents or approve their actions.
- **Fits the session model:** one channel per session (`channelId` = session id), no mapping onto someone else's
  workspaces or chats.
- **Own UI:** the waiting view, answers, and later approve/deny can be designed for this flow instead of fitted into
  chat-bot limits.

### Authentication and authorization

Answers and approvals steer the agent, so both who connects and who may answer matter:

- **Authentication (Pushca):** opening a connection requires an account id **and** its secret; a copied chat link
  alone is not enough.
- **Authorization (per session):** `POST /{agentName}/start` will carry the **initiator's id**, the Pushca account
  that started the session (planned field in `StartAgentRequest`). The session's questions go to that account, and
  agent-proxy accepts answers and approvals only from it (plus explicitly allowed admin accounts, if needed), not
  from every account that can connect. The initiator id is part of the session's state and is saved in
  `session-state.json`, so it still applies after archive and resume.
- **agent-proxy's own Pushca credentials** (account id and secret) come from the environment, like the GitHub token
  and the API key: a Kubernetes Secret (`agent-proxy-secrets`), never a file.

## 4. Claude asks the admin: the `ask_human` MCP tool

agent-proxy runs its own MCP server with an `ask_human(question)` tool. An answer can come back two ways,
depending on how fast the admin is:

- **Quick answer:** the tool call waits up to `agent-question.quick-answer-timeout` (e.g. 3 min). An answer in
  that window is the tool's result, and Claude continues the same turn. No extra run, no extra cost.
- **Long wait:** after the window the tool returns, the run ends, and the session is archived until the answer
  arrives. Then agent-proxy resumes it with the answer in the resume prompt.

Why not keep the call open for hours: Claude Code allows it (the default per-call limit is about 28 h), but the
Claude process would stay alive and hold a session slot all that time, the session time limit (60 min) would end
it, and an agent-proxy or pod restart would lose the call and the answer.

### Flow

1. **Claude calls `ask_human(question)`.** agent-proxy records the question and persists it with the session
   right away (`session-state.json`, so it survives a restart). The session becomes `WAITING`; the process is
   still alive (`markWaiting`). The call blocks up to the quick-answer window.
2. **Quick path: the answer arrives in the window.** The tool returns the answer, the session goes back to
   `RUNNING`, and Claude continues the same turn.
3. **Long path: the window passes.** The question is marked *deferred*, and the tool returns a fixed text:
   *"No answer yet. The question stays open. End your turn now without doing more work; you will be resumed
   with the answer."* Claude ends its turn and the process exits on its own. The finish handler sees a deferred
   question: it does **not** mark the session `DONE` or write a response; it archives the session, still
   `WAITING`, and frees the slot.
4. **The deferred answer arrives** (HTTP now, Pushca later; possibly hours later). agent-proxy resumes the
   session (`claude -p "<answer prompt>" --resume <sessionId>`) with:
   *"The human answered your question «Q»: «A». Continue the task and return your final answer as before."*

The tool call always gets a result before the session is archived, so the conversation never holds an
unanswered tool call, and the answer arrives as an ordinary new user message.

```java
@Tool(name = "ask_human", description = "Ask the human operator a question ...")
String askHuman(@ToolArg(name = "question") String question, McpTransportContext context) {
    ClaudeAgentSession session = caller(context);               // session id + token headers, see "Wiring"
    PendingQuestion asked = questions.ask(session, question);   // persisted with the session
    try (var _ = registry.markWaiting(session.getSessionId())) {
        return asked.awaitAnswer(quickAnswerTimeout)
                .orElseGet(() -> {
                    asked.defer();                              // atomic: an answer can't slip in between
                    return END_TURN_AND_WAIT;
                });
    }
}
```

### Edge cases

- **Answer just after the window**, while Claude is still ending its turn: store it, and resume as soon as the
  process has exited. "Answer arrived" and "window expired" must be decided atomically (one compare-and-set on
  the question), so an answer is neither lost nor delivered twice.
- **Claude ignores the instruction and finishes anyway** (e.g. writes a final report): still archive as
  `WAITING` and resume when the answer arrives; the answer may change the report.
- **Several questions in one turn:** the tool description asks for one question, then stop. A session with
  several deferred questions is resumed once all of them are answered, with every answer in one prompt.
- **Restart during the quick window:** the Claude process dies with agent-proxy. The question is already
  persisted, so a later answer can still resume the session from disk.
- **No answer ever:** waiting sessions expire after `agent-question.answer-ttl` (e.g. 7 days) and are cleaned
  up (see `spec/known_gaps.md`, gap 1).

### Session state

- `WAITING` covers both phases from the caller's point of view ("waiting for a human"); whether the process is
  alive or archived is internal. `/agent-status` reports `WAITING` and the pending questions.
- `ARCHIVED` stays what it is: a session archived on request.
- Already implemented and reused: `markWaiting` (quick phase), `archive` / `resume` (long phase; `resume` needs
  a variant taking the answer prompt instead of the agent's `resumePrompt`). Archiving a silent `WAITING` session
  after the idle timeout becomes a safety net: with a quick window shorter than the idle timeout it never fires.

### Answers

- `GET /agent-question/{agentName}/{sessionId}`: the session's unanswered questions.
- `POST /agent-question/{agentName}/{sessionId}/{questionId}` with `{"answer": "..."}`: one endpoint for both
  phases. A question whose tool call is still waiting gets the answer handed to it; a deferred one records the
  answer and triggers the resume. Pushca delivers answers through the same service later (section 3).

### Library

Micronaut MCP 2.1.0, `io.micronaut.mcp:micronaut-mcp-server-java-sdk`, managed by the Micronaut platform BOM
(5.2.5), on the official MCP Java SDK 2.0.1. HTTP transport, endpoint `/mcp`:

```properties
micronaut.mcp.server.transport=HTTP
micronaut.mcp.server.info.name=agent-proxy
micronaut.mcp.server.info.version=${AGENT_PROXY_VERSION:0.1}
```

Checked in the library sources:

- **Blocking is safe.** HTTP without `micronaut.mcp.server.reactive` uses the SDK's stateless sync server, which
  runs synchronous tools with `immediateExecution = false`, i.e. `subscribeOn(Schedulers.boundedElastic())`: off
  the Netty event loop, so a tool may block for the quick-answer window.
- **Request headers.** The default `McpTransportContextExtractor<HttpRequest<?>>` keeps only standard MCP
  headers (protocol version, `Mcp-Session-Id`, `Last-Event-Id`, host, locale, principal). agent-proxy provides its
  own `@Primary` extractor (the SDK interface is public) that also copies the session headers below; the tool
  method takes an `McpTransportContext` parameter and reads them with `context.get(...)`.
- **No server-to-client messages over HTTP** (guide: "Server to Client communication via an SSE stream is not
  yet supported"), so no progress notifications during the wait.

### Wiring per run

agent-proxy adds its server on the command line, next to whatever MCP servers the agent itself configures:

```json
{"mcpServers": {"agentproxy": {
  "type": "http",
  "url": "http://127.0.0.1:8080/mcp",
  "headers": {"X-Agent-Session-Id": "<sessionId>", "X-Agent-Session-Token": "<per-session secret>"},
  "timeout": 300000
}}}
```

- `--mcp-config` takes this as an inline string (CLI help: "Load MCP servers from JSON files or strings").
- `--allowedTools` gets `mcp__agentproxy__ask_human` appended to the agent's own `allowedTools`: in headless
  mode an MCP tool must be pre-allowed.
- `--disallowedTools AskUserQuestion` (already passed) keeps Claude on `ask_human`.
- The token is random per run; the tool accepts a call only if it matches the running session (constant-time
  comparison).

### Timeouts

| Limit | Value | Why |
|---|---|---|
| `agent-question.quick-answer-timeout` | e.g. 3 min | how long a tool call waits before the long path |
| Claude Code: HTTP MCP call without response or progress | aborted after 5 min | the quick window must stay below it (no progress can be sent) |
| per-server `"timeout"` in `--mcp-config` | quick window + margin, e.g. 5 min | hard limit per call; at least 1000 ms also floors the idle check |
| `claude.session.idle-timeout` | 10 min | longer than the quick window, so a quick wait never trips it |
| `agent-question.answer-ttl` | e.g. 7 days | when an unanswered, archived session is cleaned up |

### Cost

A quick answer costs nothing extra. A long wait costs a resume: the whole conversation is sent to the API again.
Within minutes prompt caching makes that cheap; after hours the cache has expired and the full context is paid
again. Fine for review-sized sessions, but worth knowing for very long ones.

## 5. The admin approves actions: `--permission-prompt-tool`

`--permission-prompt-tool mcp__agentproxy__approve` sends Claude Code's permission prompts ("may I run
`./mvnw deploy`?") to a second tool on the same MCP server, which forwards them to the admin through
`HumanChannel.approve`. Agents keep strict permissions; a human approves exceptions live.

- Claude Code waits for the tool's server to connect before the first turn, up to `MCP_TIMEOUT` (30 s).
- It can't approve an MCP tool marked as requiring user interaction (an `allow` becomes a deny).
- **verify** the tool's input/output JSON contract (allow/deny, updated input, message). The docs link it
  from the errors page ("permission-prompt-tool returned an invalid result"), which was not readable in full.
- No answer before the deadline: deny, with a message telling Claude the request was not approved.

## 6. New instructions to an existing session

Instructions the admin starts (not answers to a question) run as follow-up turns of the same conversation:

```bash
claude -p "<instruction>" --resume <sessionId> --output-format stream-json --verbose ...  # same flags as the first run
```

- Resuming works from any directory and keeps the conversation's context; run it in the same working
  directory so file paths stay valid.
- `POST /{agentName}/sessions/{sessionId}/messages` with `{"instruction": "...", "interrupt": false}`.
  - **Queue** (default): if a run is in progress, the instruction starts a new run when it finishes.
  - **Interrupt:** stop the running process, then resume with the instruction. Per the docs the interrupted
    turn stays as it is "and your next prompt drives the conversation". SIGINT ends the turn cleanly;
    SIGTERM (`Process.destroy()`) exits with code 143 and records no result for that turn. To have Claude
    finish the interrupted work first, set `CLAUDE_CODE_RESUME_INTERRUPTED_TURN=1`.
- A per-session registry holds the running process and the queue of pending instructions.

### Rejected alternatives

- **One long-lived process with `--input-format stream-json`**, writing new messages to its stdin: the
  docs don't specify the stdin message format or an interrupt message for the CLI (only through the Agent
  SDK). Building on undocumented behaviour is not acceptable.
- **Agent SDK (TypeScript/Python) sidecar**: documented and supports `interrupt()`, but adds a Node or
  Python process to a Java service for no gain over resume-based runs plus `ask_human`.

## 7. Security

- `/mcp` is served on the public HTTP port: the tools accept a call only with the per-session secret
  (`X-Agent-Session-Token`) of the session in `X-Agent-Session-Id`, so outsiders can't inject questions or
  approvals. The answer endpoint needs its own protection (Pushca, or authentication) before it is exposed.
- Pushca authenticates every connection (account id and secret), and agent-proxy accepts answers and approvals
  for a session only from its initiator (section 3).
- Cloned repositories are untrusted input (prompt injection). The agent's `deny` rules (which apply
  immediately, even in untrusted folders) are the guardrail; never run with `--dangerously-skip-permissions`.
- All sessions run as the same user in one container. An agent with Bash access could read or change
  another session's folder. Restrict file tools to the session (`Edit(//var/lib/agent-proxy/sessions/<sessionId>/**)`); for agents
  with Bash, consider one container (or Kubernetes Job) per session. Claude Code's Bash sandbox needs
  `enableWeakerNestedSandbox` inside Docker, which the docs say considerably weakens it.
- Logs and Pushca get summaries only (section 2), never raw tool results.

## 8. Implementation order

1. Done (`ClaudeAgentSessionRegistry`): process start, working directory, `--session-id`, time limit, idle
   timeout, concurrency cap, transcript file, status and response endpoints, 202 from `start`, archive and resume.
2. Done: stream-json summaries into the logs.
3. `HumanChannel` interface with an in-memory fake; `publish` wired to the runner.
4. MCP server with `ask_human` (Micronaut MCP), per-run `--mcp-config`, quick-answer window, deferred
   questions with archive and resume-with-answer, answer endpoint, answer TTL.
5. `PushcaHumanChannel`.
6. `approve` tool and `--permission-prompt-tool`.
7. Messages endpoint with queue/interrupt via `--resume`.

Verify items:

- Resolved: `allow` rules from `--settings` do **not** apply in an untrusted folder in `-p` mode (Claude Code
  warns "Ignoring … permissions.allow entries"); allowed tools come from the agent's `agent.json` and are passed
  as `--allowedTools`.
- Resolved: `--mcp-config` accepts an inline JSON string (CLI help).
- Resolved: a `@Tool` method reads request headers through a custom `McpTransportContextExtractor` (section 4).
- Open: `--permission-prompt-tool` input/output contract.

## Sources

Claude Code docs, read 2026-11-12:

- Headless / programmatic runs: https://code.claude.com/docs/en/headless
- CLI flags: https://code.claude.com/docs/en/cli-reference
- MCP (project servers, timeouts, env expansion): https://code.claude.com/docs/en/mcp
- Permissions (rule syntax, modes, trust): https://code.claude.com/docs/en/permissions
- Settings: https://code.claude.com/docs/en/settings
- Environment variables: https://code.claude.com/docs/en/env-vars
- Sandboxing: https://code.claude.com/docs/en/sandboxing
- Agent SDK streaming input: https://code.claude.com/docs/en/agent-sdk/streaming-vs-single-mode

Libraries:

- Micronaut MCP: https://micronaut-projects.github.io/micronaut-mcp/latest/guide/
- MCP Java SDK: https://github.com/modelcontextprotocol/java-sdk
