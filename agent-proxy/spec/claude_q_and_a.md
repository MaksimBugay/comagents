# Claude Code sessions with remote Q&A

Status: design, not implemented.

agent-proxy prepares a session folder (`POST /{agentName}/start`: clone repo, copy agent files), then runs
Claude Code headless in it. While Claude works, a human admin follows its output and answers its questions
remotely through Pushca.

Everything below relies on documented Claude Code features; sources are listed at the end. Items marked
**verify** were not fully confirmed in the docs and need a prototype before building on them.

## Goals

1. Run Claude Code headless per session, in the session's working directory, with the agent's kick-off prompt.
2. Stream Claude's progress into agent-proxy logs and to the admin (Pushca).
3. Let Claude ask the admin questions mid-task and continue with the answer, in the same run.
4. Let the admin approve or deny actions the agent's permissions don't already allow.
5. Let the admin (or agent-proxy) send new instructions to an existing session.

## Overview

```
admin ◄──── Pushca ────► HumanChannel (agent-proxy)
                           ▲   ▲            │
            progress events│   │questions,  │ new instructions
                           │   │approvals   ▼
                 ClaudeRunner  MCP server /mcp ◄── tool calls (HTTP): ask_human, approve
                   │    ▲                                │
       starts /    │    │ stream-json (stdout)           │
       resumes     ▼    │                                │
                 claude -p  (child process, cwd = session working dir) ───┘
```

## 1. Running Claude Code

One child process per run, started with `ProcessBuilder` (works in the GraalVM native image). No terminal is
needed: `claude -p` runs without a TTY.

- **Working directory:** the cloned repo `/tmp/<sessionId>/<repo>`, or `/tmp/<sessionId>/<agentName>` when
  nothing was cloned (same rule as `AgentWithClaudeCodeSetupService`).
- **Session id:** reuse our `sessionId` as Claude's session id. `--session-id` accepts any valid UUID, so
  agent-proxy never has to read Claude's id back.
- **Environment:** inherits the container env (`ANTHROPIC_API_KEY`, `DISABLE_AUTOUPDATER=1`), plus the
  variables in section 4.

First run (`POST /{agentName}/start`, after setup):

```bash
claude -p "<kick-off prompt>" \
  --session-id <sessionId> \
  --output-format stream-json --verbose \
  --settings .claude/settings.json \
  --mcp-config '<agent-proxy MCP server, see section 4>' \
  --allowedTools "mcp__agentproxy__ask_human" \
  --disallowedTools "AskUserQuestion" \
  --permission-prompt-tool mcp__agentproxy__approve
```

Follow-up runs (section 6) use `--resume <sessionId>` instead of `--session-id <sessionId>`.

### Permissions in headless mode

- In `-p` mode a tool call that needs approval and isn't pre-allowed is **denied**, not left waiting
  (unless `--permission-prompt-tool` handles it, section 5).
- A headless run never shows the workspace trust dialog. Per the docs, `permissions.allow` rules and
  `permissions.additionalDirectories` in a **project** `.claude/settings.json` only apply after the folder
  is trusted; `deny` rules apply immediately. So agent-proxy passes the agent's settings explicitly with
  `--settings .claude/settings.json`, which is a command-line settings source. **verify** that `allow`
  rules from `--settings` apply in an untrusted folder (documented for MCP approvals, not for permissions).
- File access outside the working directory: `--add-dir /tmp/<sessionId>` per agent if needed.
- Absolute paths in permission rules use a double slash: `Edit(//tmp/<sessionId>/**)`.

### HTTP API

`POST /{agentName}/start` must not wait for Claude: a whole-project review can take many minutes.

- `POST /{agentName}/start` sets up the session, starts the run, returns **202 Accepted** with the session id.
- `GET /{agentName}/sessions/{sessionId}` returns the state: `RUNNING`, `WAITING_FOR_HUMAN`, `DONE`,
  `FAILED`, `TIMED_OUT`, plus the result when there is one.

### Limits

- **Time limit** per run (configurable). On expiry, stop the process and record `TIMED_OUT`.
- **Concurrency cap** per container (configurable, e.g. 2–4). Claude Code's docs recommend 4 GB+ RAM, and
  each run is a separate process. Reject (429) or queue starts above the cap.
- `k8s/deployment.yaml` must raise its memory limit (currently 128 MiB) and add writable `emptyDir`
  volumes for `/tmp` and `/home/nonroot` (Claude Code writes `~/.claude`), because the root filesystem is
  read-only there.

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

1. Append it to `/tmp/<sessionId>/<agentName>/claude-stream.jsonl`: the full transcript, for debugging.
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

    /** Asks the admin a question; completes with the answer, or empty when the deadline passes. */
    Optional<String> ask(UUID sessionId, String question, Duration deadline);

    /** Asks the admin to allow or deny a tool call. */
    ApprovalDecision approve(UUID sessionId, ApprovalRequest request, Duration deadline);
}
```

- `PushcaHumanChannel` is the production implementation; tests use an in-memory fake.
- One Pushca channel per session, addressed by `sessionId`.
- Pushca must authenticate the admin side: answers and approvals steer the agent.

## 4. Claude asks the admin: the `ask_human` MCP tool

agent-proxy runs its own MCP server, and Claude calls its tool when it needs a decision. The call blocks
(on a virtual thread) until the admin answers, then Claude continues the same turn with the answer.

### Library

Micronaut MCP 2.1.0, `io.micronaut.mcp:micronaut-mcp-server-java-sdk`. Built for Micronaut 5, based on the
official MCP Java SDK (2.x, GA). Releases 1.0.0 and later are not pre-releases. HTTP transport, endpoint
`/mcp` by default.

```java
@Singleton
class HumanTools {

    @Tool(description = "Ask the human operator a question and wait for the answer. "
            + "Use whenever a decision needs a human; nobody else can answer.")
    String askHuman(String question) {
        // sessionId from the X-Session-Id header (verify, see below)
        return humanChannel.ask(sessionId, question, ANSWER_DEADLINE)
                .orElse("No answer from the operator. Proceed with your best assumption and record it in the report.");
    }
}
```

### Wiring per run

agent-proxy adds its server on the command line, so agent repos don't need to know about it:

```json
{"mcpServers": {"agentproxy": {
  "type": "http",
  "url": "http://127.0.0.1:8080/mcp",
  "headers": {"X-Session-Id": "<sessionId>", "Authorization": "Bearer <per-session secret>"},
  "timeout": 3600000
}}}
```

- `--allowedTools "mcp__agentproxy__ask_human"`: in headless mode an MCP tool must be pre-allowed.
- `--disallowedTools "AskUserQuestion"`: removes Claude Code's built-in question tool, which nobody can
  answer in a headless run, so Claude uses `ask_human` instead.
- The kick-off prompt tells Claude to use `ask_human` for decisions.
- **verify** that `--mcp-config` accepts the inline JSON string (docs: "JSON files or strings", no example).
- **verify** that a `@Tool` method can read the request's `X-Session-Id` header.

### Waiting a long time

| Limit | Default | Setting |
|---|---|---|
| Wall-clock limit per tool call | ~28 h | per-server `"timeout"` in ms (above: 1 h), or `MCP_TOOL_TIMEOUT` |
| No response and no progress (HTTP) | 5 min | `CLAUDE_CODE_MCP_TOOL_IDLE_TIMEOUT=0` disables it |
| Moving slow calls to the background | off in `-p` | leave `CLAUDE_AUTO_BACKGROUND_TASKS` unset |

Micronaut MCP can't send progress notifications ("Server to Client communication via an SSE stream is not
yet supported"), so the 5-minute idle check would abort any wait longer than 5 minutes:
`CLAUDE_CODE_MCP_TOOL_IDLE_TIMEOUT=0` is required on the process. A per-server `timeout` of at least
1000 ms also acts as a floor for the idle check.

agent-proxy's own answer deadline should be shorter than the per-server `timeout`, so the tool returns the
"no answer" text instead of Claude Code aborting the call.

agent-proxy's own idle timeout (`claude.session.idle-timeout`, 10 min by default) kills and deletes a session that
prints nothing for that long, and a session waiting in `ask_human` or `approve` prints nothing. The watchdog must
treat a pending human question as activity (e.g. pause the idle timer while `HumanChannel.ask`/`approve` waits),
or a slow admin gets the session deleted.

### Session state

While `ask_human` waits, the session state is `WAITING_FOR_HUMAN`; the status endpoint returns the
pending question.

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

- `/mcp` is served on the public HTTP port: require the per-session secret in `Authorization` and check it
  matches `X-Session-Id`, so outsiders can't inject questions, answers, or approvals.
- The admin side of Pushca must be authenticated.
- Cloned repositories are untrusted input (prompt injection). The agent's `deny` rules (which apply
  immediately, even in untrusted folders) are the guardrail; never run with `--dangerously-skip-permissions`.
- All sessions run as the same user in one container. An agent with Bash access could read or change
  another session's folder. Restrict file tools to the session (`Edit(//tmp/<sessionId>/**)`); for agents
  with Bash, consider one container (or Kubernetes Job) per session. Claude Code's Bash sandbox needs
  `enableWeakerNestedSandbox` inside Docker, which the docs say considerably weakens it.
- Logs and Pushca get summaries only (section 2), never raw tool results.

## 8. Implementation order

1. `ClaudeRunner`: process start, working directory, `--session-id`, time limit, concurrency cap,
   transcript file, status endpoint, 202 from `start`.
2. Stream-json summaries into the logs.
3. `HumanChannel` interface with an in-memory fake; `publish` wired to the runner.
4. MCP server with `ask_human` (Micronaut MCP), per-run `--mcp-config`, idle/timeout settings.
5. `PushcaHumanChannel`.
6. `approve` tool and `--permission-prompt-tool`.
7. Messages endpoint with queue/interrupt via `--resume`.

Prototype the **verify** items before steps 4–6:

- `allow` rules from `--settings` apply in an untrusted folder in `-p` mode.
- `--mcp-config` accepts an inline JSON string.
- A Micronaut MCP `@Tool` method can read request headers.
- `--permission-prompt-tool` input/output contract.

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
