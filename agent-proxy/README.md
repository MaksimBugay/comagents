# agent-proxy

Micronaut 5 (Java 25) microservice, built as a GraalVM native image for Docker/Kubernetes.

## API

| Method | Path                  | Description                     |
|--------|-----------------------|---------------------------------|
| GET    | `/info`               | This agent executor, whatever agent it runs: its `harnessProvider` (`agent-proxy.ai-harness-provider`), version, device id, `maxSlots` (`claude.session.max-concurrent`), `freeSlots` (`maxSlots` minus its sessions running, waiting or being set up), `archivedSessions` (archived sessions on disk: no slot now, one each on resume) and `finishedSessions` (finished sessions whose data is still on disk until it is deleted: no slot); the start reply has them too |
| GET    | `/agent-status/{agentName}/{sessionId}` | Session state (`RUNNING`, `WAITING`, `DONE`, `FAILED`, `TIMED_OUT`, `KILLED`, `ARCHIVED`), times, and whether a response is available |
| GET    | `/health/liveness`    | Kubernetes liveness probe       |
| GET    | `/health/readiness`   | Kubernetes readiness probe      |

```console
$ curl localhost:8080/info
{"harnessProvider":"CLAUDE_CODE","version":"0.1","deviceId":"cb960a48877d8a7995f18bd400c563fd1eeb9810cf61ad584e4f2478e7e1abb3","maxSlots":3,"freeSlots":3,"archivedSessions":0,"finishedSessions":0,"sequenceId":"3f1c9a52-7d4e-4b8a-9c21-5e6f7a8b9c0d"}
```

### Over Pushca

Sessions are started and their responses fetched over Pushca only, by the orchestrators (agent-orchestrator, which
has the HTTP API for them); the rest of the API works over Pushca too:

- **The executor's own client:** signed in to workspace `comagents-main` as `agent-executor@comagents.com`,
  application `<AI_HARNESS_PROVIDER>_EXECUTOR` (e.g. `CLAUDE_CODE_EXECUTOR`), device id = `deviceId`. It is opened
  on startup and kept open by the Pushca client itself, which signs in or reconnects every 2 s while it isn't open.
  A `PUSHCA_API_KEY` that Pushca refuses (HTTP 401/403) stops the startup.
- **Requests:** an orchestrator sends a `ComagentsMessage` whose payload is one of the requests below, with the
  request's `sequenceId` as the message id. Each request is handled on its own thread.
- **Replies:** the reply goes back to the request's `originatorDeviceId` device of
  `agent-orchestrator@comagents.com` (`comagents-main`, `AGENT_ORCHESTRATOR`), with the request's `sequenceId` as the
  message id. When the request fails, the reply carries an `error` (`errorMessage`, `errorClassName`) in place of the
  payload, under the class the payload would have had.

| Request (`com.comagents.commons.request`) | As over HTTP | Reply (`com.comagents.commons.response`) | Errors |
|---|---|---|---|
| `StartAgentSessionRequest` | — (agent-orchestrator's `POST /start`) | `AgentInfoResponse` | anything that stops the start: an invalid request (`ConstraintViolationException`), a session id in use (`ClaudeSessionExistsException`), no setup service for the agent's harness (`NoAgentSetupServiceException`), … |
| `AgentStatusRequest` (`agent`, `sessionId`, `sequenceId`, `originatorDeviceId`) | `GET /agent-status/{agentName}/{sessionId}` | `AgentStatusResponse` (the request and the `AgentStatus`) | `AgentSessionNotFoundException`: never started, or already cleaned up |
| `AgentResponseRequest` (`agent`, `sessionId`, `sequenceId`, `originatorDeviceId`) | — (agent-orchestrator's `GET /agent-response/{sessionId}`) | `AgentResponseReply` (the agent's final answer as an agent context, per the agent's `responseSchema` or one `STRING` item `response`, with the start request, as the `AgentSessionResponse`, which keeps the start request's `sequenceId`); after a first fetch, the session's files are deleted 5 minutes later | `AgentSessionNotFoundException`; `AgentResponseNotReadyException` while the session is `RUNNING`, `WAITING` or `ARCHIVED` (every session leaves a response when it ends) |

As soon as a session's response is on disk, the `AgentSessionResponse`, as a `ComagentsMessage`
(`<base64 of {"payloadString": "com.comagents.commons.response.AgentSessionResponse::<the JSON>", "payloadClassName": "com.comagents.commons.response.AgentSessionResponse"}>`),
is sent with the start request's `sequenceId` as the message id to the start request's `originatorDeviceId` device,
and also to its `agentResponseWaiterDeviceId` device when there is one (`comagents-main`, whatever their account and
application: a device id is one agent-proxy or orchestrator in the cluster). Session files and chat are deleted as soon
as the orchestrator, which keeps the response, sends a `SessionCleanupRequest` (`agent`, `sessionId`, `sequenceId`;
nothing is answered). Without one (a delivery that broke), they are deleted 5 minutes after the response was sent or
first fetched, or 1 hour after the session finished if never fetched. Archived sessions are kept. After a restart,
sessions that are not archived are deleted with their chats.

Every message to an orchestrator (replies, errors, and the `AgentSessionResponse` sent once a response is on disk)
goes through one queue and over the one connection, one by one. A send that finds the connection broken is retried
every second until `AGENT_PROXY_MESSAGE_TIMEOUT` (30 s) has passed since it was queued; a message not sent by then is
dropped with an error logged, and the orchestrator, which has timed out meanwhile, asks again.

### Session chat

Every session mirrors its output into its own Pushca chat: a close private channel with id and name
`<agent name>_session_<session id>` in workspace `comagents-secure-chat` (application `CHAT_ROOM`), owned by the
agent (account `<agent name>@comagents.com`, the session id as device id). Its only member is the start request's
`actorAccountId`, on any of their devices. `loggingLevel` `INFO` (default) leaves out thinking and thinking-token
progress, `DEBUG` shows them too, in the chat and in agent-proxy's log. The chat is removed together with the
session's files. When the agent gives a final answer, the full answer is the chat's last message. When Pushca
is unavailable, the session runs without a chat.

When the chat is created, agent-proxy logs its join link, which opens it on the chat page (`PUSHCA_CHAT_URL`) as the
actor, in the right workspace:

```text
https://secure.fileshare.ovh/chat-room.html?channelId=code_review_session_<session id>&accountId=<actor>&avatar=human-manager&workspaceId=comagents-secure-chat
```

```json
{"sessionId": "…", "agent": "CODE_REVIEW", "sequenceId": "…", "originatorDeviceId": "…", "agentResponseWaiterDeviceId": "…", "agentCallSequence": [], "actorAccountId": "Maks", "loggingLevel": "INFO",
 "agentContext": {"items": [
   {"type": "GITHUB_REPOSITORY", "value": "https://github.com/<org>/kn-challenge.git", "primary": true},
   {"type": "FILE", "name": "requirements.md", "value": "<S3 download URL>", "description": "acceptance criteria"},
   {"type": "STRING", "name": "focus", "value": "security"}]}}
```

### Agent context

`agentContext` is optional. Its `items` are typed by `type`:

- `STRING`: a `name` and a text `value`;
- `FILE`: the file `name` and its download URL in S3-compatible object storage as `value`;
- `GITHUB_REPOSITORY`: the clone URL as `value` (`https://github.com/<owner>/<repo>[.git]`, checked on arrival), named `<owner>/<repo>` after it (`…/acme/kn-challenge.git` → `acme/kn-challenge`), cloned into a folder named `<repo>`,
  and optionally `"primary": true`.

Every item needs a value and can have a `description` of what it is for, shown to the agent with the item. Items are
unique by type and name. One repository is cloned as the session's working
directory: the only one, or the primary one when there are several (at most one primary; several repositories
without one are rejected). Without a repository nothing is cloned. The agent's `agent.json` can list
`requiredItems` by `type` and/or `name` (an omitted one matches any): a context without a matching item is
rejected before anything is cloned for the session. Every other item is appended to the agent's kick-off prompt as a
"Context of this session" list (the cloned repository is not: it is the working directory): other repositories and
files are listed by URL only, not cloned or downloaded, for the agent to fetch if useful; `STRING` items are given as
text. All names and values together may take at most 64 KiB
(UTF-8): the context goes into the prompt, which Claude Code gets as one command-line argument (128 KiB at most on
Linux). An invalid context is rejected with **400** and a message saying what is wrong (e.g. "Duplicate item STRING
focus").

### Questions to the expert (`ask_expert`)

agent-proxy runs its own MCP server on `/mcp` (stateless HTTP). Every Claude Code run gets it with `--mcp-config`
(server `agentproxy`) and `mcp__agentproxy__ask_expert` pre-allowed. When the agent calls `ask_expert`, the question
is posted to the session chat as `Question: …`, the session is `WAITING`, and the next message written in the chat
is the tool's result. Without an answer within `AGENT_QUESTION_ANSWER_TIMEOUT`, the tool returns `NO ANSWER YET …`
(explicitly not an answer). As soon as that result shows up in the run's output, the run is stopped and the session
archived (`ARCHIVED`, no response written, slot freed), so the agent can't act on it; the chat says so. The next message in the chat resumes it
(`claude -p "The expert answered your question …" --resume <session id>`), also after a restart. Without a chat,
the agent is told to go on with its best judgement. One question per session at a time.

Only the run itself can call the tool: its `--mcp-config` carries the session id and a random per-run token
(`X-Agent-Session-Id`, `X-Agent-Session-Token`), and other calls get an error result.

### Calling other agents (`mcp__agentproxy__<agent name>`)

An agent calls another agent through agent-proxy's MCP server when its `agent.json` `allowedTools` name
`mcp__agentproxy__<agent name>` (any tool of the server but `ask_expert`). At session setup the called agent's
`agent.json` is read from the comagents sources: its `exposeAsMcp` gives the tool's description and input schema
(the called agent's agent context, `{"items": [...]}`), its `responseSchema` the shape of the tool's result (appended to
the description; without one, the one `STRING` item `response`), its `readOnly` the tool's `readOnlyHint`. A called agent
without `exposeAsMcp` fails the setup. The tool is added to the MCP server once and stays while agent-proxy runs; it
is also kept in the session's agent folder (`agent-tools.json`), so the tools of archived sessions are added again
after a restart.

A call works like `ask_expert`, with the session `WAITING`:
1. the called agent's session is started with the orchestrator's `POST /start` (`AGENT_PROXY_ORCHESTRATOR_URL`), with
   this executor's `deviceId` as `agentResponseWaiterDeviceId` and the caller's `agentCallSequence`, which the
   orchestrator ended with the caller when it started it and now grows by the called agent. A call of the caller itself
   or of an agent already in the chain is refused: calls must not loop;
2. the called session's `AgentSessionResponse`, which its executor sends to this executor's device too, is the tool's
   result: its agent context as JSON, checked against the called agent's response schema (a response that doesn't
   match is an error result with the reason and the response). No MCP `outputSchema`: with it the MCP server would
   reject the `NO ANSWER YET …` result below. A response that comes while its call is still being started is kept for
   it; one without a caller here (killed meanwhile, or delivered twice) is dropped;
3. without it within `AGENT_QUESTION_ANSWER_TIMEOUT` (counted from the call), the tool returns `NO ANSWER YET …`, and
   the session is archived as for an unanswered question. The response resumes it whenever it comes
   (`claude -p "The <agent name> agent you called responded: …" --resume <session id>`), also after a restart. The
   response is first stored in the session's agent folder (`agent-call-response.json`): when all slots are busy, the
   session is resumed with it as soon as a run frees one, or at startup.

A refused start is an error result the agent reads: for a 429 (no executor of the called agent has a free slot) it says
the agent is busy and was not called, to call it again later or continue without it; otherwise the orchestrator's
status and message.

## How sessions run

### Claude Code process

One `claude -p` child process per run, in the session's working directory: the cloned repo
`<sessions-dir>/<sessionId>/<repo>`, or `<sessions-dir>/<sessionId>/<agentName>` when nothing was cloned. No terminal
is needed. Model, effort, allowed tools and prompts come from the agent's `agent.json`:

```bash
claude -p "<kickOffPrompt>" \
  --session-id <sessionId> \
  --output-format stream-json --verbose \
  --disallowedTools AskUserQuestion \
  --mcp-config '{"mcpServers":{"agentproxy":{"type":"http","url":"…/mcp","headers":{…},"timeout":…}}}' \
  [--model <model>] [--effort <effort>] \
  --settings '{"permissions":{"blockReadsOutsideWorkingDirectories":true}[,"sandbox":<agent.json sandbox, allowWrite + the m2 folder>]}' \
  --allowedTools <agent.json allowedTools…> mcp__agentproxy__ask_expert
```

- **Session id:** our `sessionId` is Claude's session id, so agent-proxy never reads it back. Resumed runs use
  `--resume <sessionId>` with the agent's `resumePrompt`, with the expert's answer, or with the called agent's response.
- **Environment:** only an allowlist of the container's: `PATH`, `HOME`, `LANG`, `LC_ALL`, `TZ`,
  `DISABLE_AUTOUPDATER`, `ANTHROPIC_*` (model settings such as `ANTHROPIC_MODEL`) and `CLAUDE_CODE_*`. agent-proxy's
  own secrets (`SOURCES_REPO_ACCESS_TOKEN`, `PUSHCA_API_KEY`, ...) never reach the run, and neither does an LLM
  provider key: agent-proxy has none. The run reaches its model through the llm-watchdog, which holds the provider's
  key, analyzes each call and forwards it: `ANTHROPIC_BASE_URL=<AGENT_PROXY_LLM_WATCHDOG_URL>` and
  `ANTHROPIC_API_KEY=<sessionId>` (worthless anywhere else; it tells the watchdog the session). Plus
  `CLAUDE_CONFIG_DIR=<sessions-dir>/<sessionId>/<agentName>/claude-state`
  (Claude Code's own state for the session: settings, the conversation `--resume` reads; deleted with the session,
  kept by an archive, never shared),
  `TMPDIR=<sessions-dir>/<sessionId>/<agentName>/tmp` (the run's own temp folder in place of the container's, so
  parallel sessions never share temp files; deleted with the session),
  `MAVEN_OPTS=-Dmaven.repo.local=<sessions-dir>/<sessionId>/<agentName>/m2/repository` and
  `MAVEN_USER_HOME=<sessions-dir>/<sessionId>/<agentName>/m2` (the run's own Maven repository and wrapper downloads
  in place of the shared `~/.m2`, so parallel sessions never overwrite each other's artifacts; deleted with the
  session, writable in the agent's sandbox), and `CLAUDE_CODE_RESUME_INTERRUPTED_TURN=1`
  when resuming with the `resumePrompt` (finish the interrupted turn first; not when resuming with an answer or a
  response).
- **stdin** is `/dev/null`: with an open pipe, `claude -p` waits 3 s for input on every run.

### Permissions in headless mode

- A tool call that needs approval and isn't pre-allowed is **denied**, not left waiting.
- Every session starts in a new, never trusted folder, so `permissions.allow` (and `additionalDirectories`) in a
  project `.claude/settings.json` are ignored (Claude Code warns "Ignoring N permissions.allow entries"); passing
  the file with `--settings` doesn't help. `deny` rules apply immediately.
- So an agent's `.claude/settings.json` holds only `deny` rules, and its allowed tools are `allowedTools` in
  `agent.json`, passed as `--allowedTools` (command-line flags aren't subject to the trust check). A cloned
  repository's own `allow` rules are never passed on. MCP tools must be pre-allowed too.
- File access outside the working directory needs `--add-dir`. Absolute paths in permission rules use a double
  slash: `Edit(//var/lib/agent-proxy/sessions/<sessionId>/**)`.
- Every run gets `permissions.blockReadsOutsideWorkingDirectories`: `Read`, `Grep`, `Glob` and `LSP` refuse paths
  outside the working directory, so a session can't read another session's folder with them. Under an agent's
  `sandbox`, sandboxed Bash also loses read access to `HOME`, `/home`, `/root`, `/mnt`, `/srv` and the like (not to
  `/var` or `/tmp`).

### Output

`--output-format stream-json --verbose` prints one JSON event per line (`system`/`init`, `assistant` text and tool
calls, `user` tool results, `result` with the final answer and cost). A virtual thread per run reads it
continuously (stderr merged), which also keeps the process from stalling on a full pipe. Each line is appended to
`<sessions-dir>/<sessionId>/<agentName>/claude-stream.jsonl` (full transcript); a one-line summary is logged and
posted to the session chat; the `result` event's text becomes the session's response.

Summaries, never raw events: tool results can hold whole files or secrets the agent read. A summary names the
tool and its main argument (path, command, URL), truncates long text and never prints tool results:

```text
[code_review 3f2b…] init: model=…, tools=24, mcp=[agentproxy]
[code_review 3f2b…] tool: Read src/main/java/…/OrderService.java
[code_review 3f2b…] done: success, 42 turns, $0.87, 6m12s
```

### Limits

- **Time limit** per run, `CLAUDE_SESSION_TIMEOUT` (60 min): the process is stopped, state `TIMED_OUT`, files kept.
- **Idle timeout**, `CLAUDE_SESSION_IDLE_TIMEOUT` (10 min without output): a `RUNNING` session is stopped and
  deleted; a `WAITING` one is archived, its timer counted from when the wait started.
- **Concurrency cap**, `CLAUDE_SESSION_MAX_CONCURRENT` (3; Claude Code recommends 4 GB+ RAM per run). The slot is
  reserved before setup, so a busy service answers **429** before cloning anything.
- **Kubernetes** (`k8s/deployment.yaml`): one replica (sessions live in the pod), 4 GiB memory, read-only root
  filesystem; writable are the sessions dir (a `PersistentVolumeClaim`, `strategy: Recreate`) and `/tmp`.

### Security

- `/mcp` is on the public HTTP port: its tools accept a call only with the run's own token
  (`X-Agent-Session-Token`) for the session in `X-Agent-Session-Id`.
- Pushca authenticates every connection; a session's chat is a close private channel whose only member is its
  actor.
- Cloned repositories are untrusted input (prompt injection): the agent's `deny` rules are the guardrail; never run
  with `--dangerously-skip-permissions`.
- All sessions run as the same user in one container (see `spec/known_gaps.md`, gap 4). Claude Code's Bash sandbox
  needs `enableWeakerNestedSandbox` inside Docker, which considerably weakens it.
- Logs and chats get summaries only, never raw tool results.

## Build & run

```bash
./mvnw -f ../agent-commons/parent install   # once, and after agent-commons changes: comagents-parent (the parent
                                     # pom of this service) and agent-commons, not published
./mvnw test                          # tests
./mvnw package && java -jar target/agent-proxy-0.1.jar   # JVM

docker build -f Dockerfile -t agent-proxy:0.1 ..   # native image (no local GraalVM needed); the context is the
                                                   # comagents root, so agent-commons is built inside the image
docker run -d --name agent-proxy -p 8080:8080 \
  -v agent-proxy-sessions:/var/lib/agent-proxy/sessions \
  -e CLONE_COMAGENTS_SOURCES_URL=https://github.com/<org>/<repo>.git \
  -e SOURCES_REPO_ACCESS_TOKEN \
  -e PUSHCA_API_KEY \
  -e AGENT_PROXY_LLM_WATCHDOG_URL=http://<llm-watchdog host>:8080 \
  agent-proxy:0.1                    # token and key taken from the host env, not the command line

kubectl create secret generic agent-proxy-secrets \
  --from-literal=sources-repo-access-token="$SOURCES_REPO_ACCESS_TOKEN" \
  --from-literal=pushca-api-key="$PUSHCA_API_KEY"
kubectl apply -f k8s/deployment.yaml   # one replica; sessions on a PersistentVolumeClaim, /tmp on emptyDir
```

The Docker image is a mostly-static native binary (starts in ~10 ms, ~20 MiB RSS) on `debian:trixie-slim`,
running as non-root, with the latest [Claude Code](https://code.claude.com/docs) CLI installed for headless
use (`claude -p "<prompt>"`). `./build-image.sh` reinstalls the latest Claude Code release on every build.

## Configuration

| Env var                  | Default | Description            |
|--------------------------|---------|------------------------|
| `MICRONAUT_SERVER_PORT`  | `8080`  | HTTP port              |
| `AGENT_PROXY_VERSION`    | `0.1`   | Reported `version`     |
| `AGENT_PROXY_DEVICE_ID`  | —       | Reported `deviceId` (`agent-proxy.device-id`); when not set: the Docker container id, or the Kubernetes pod UID or `HOSTNAME` (`local` without it) with a random suffix, so every instance is a device of its own; a set one must be unique in the cluster |
| `AI_HARNESS_PROVIDER`    | set by the image: `CLAUDE_CODE` | The installed harness that runs the sessions (`agent-proxy.ai-harness-provider`); required outside the image |
| `CLONE_COMAGENTS_SOURCES_URL` | — (required) | Agent sources repository URL (`clone.comagents.sources.url`); each setup downloads only the folders it reads, `agent/<harness>/<agent name>` of the agent and of the agents it calls |
| `SOURCES_REPO_ACCESS_TOKEN`   | — (required) | Access token for that repository (`sources.repo.access-token`) |
| `AGENT_PROXY_LLM_WATCHDOG_URL` | `http://llm-watchdog:8080` | The llm-watchdog, through which Claude Code reaches its model (`agent-proxy.llm-watchdog-url`): by default its container on the same Docker network; under Kubernetes its Service, `http://llm-watchdog` |
| `PUSHCA_API_KEY`              | — (required) | API key of the organization registered in Pushca (`pushca.api-key`); a key Pushca refuses stops the startup |
| `PUSHCA_API_URL`              | `https://secure.fileshare.ovh/pushca` | Pushca server (`pushca.api-url`) |
| `PUSHCA_CHAT_URL`             | `https://secure.fileshare.ovh/chat-room.html` | Chat page; each new session chat is logged with its join link (`pushca.chat-url`) |
| `PUSHCA_CONNECT_TIMEOUT`      | `10s`        | How long signing in, opening the socket and waiting for the server to accept commands may each take (`pushca.connect-timeout`). Session chat commands on a broken connection are retried for this long plus the 2 s keep-alive interval plus 1 s: the time the Pushca client takes to reconnect by itself |
| `AGENT_PROXY_MESSAGE_TIMEOUT` | `30s`      | How long a message (payload or error) for another device may take to be sent over Pushca; sends finding the connection broken are retried every second until then, and a message not sent by then is dropped with an error logged: the orchestrator has timed out meanwhile and asks again (`agent-proxy.message-timeout`) |
| `AGENT_PROXY_DISCOVERY_INTERVAL` | `5s` | How often the executor sends a fresh `AgentInfoResponse` to all orchestrators (account `agent-orchestrator@comagents.com`, application `AGENT_ORCHESTRATOR`, any device), so they discover it and its free slots; the next send waits for the current one, and failures are only logged (`agent-proxy.discovery-interval`) |
| `AGENT_PROXY_MCP_URL`         | `http://127.0.0.1:8080/mcp` | Where Claude Code reaches agent-proxy's MCP server (`agent-proxy.mcp-url`); change it with the port |
| `AGENT_QUESTION_ANSWER_TIMEOUT` | `4m`       | How long `ask_expert` waits for an answer, and an agent tool for the called agent's response; below 5 min, after which Claude Code aborts the call (`agent-question.answer-timeout`) |
| `AGENT_PROXY_ORCHESTRATOR_URL` | `http://agent-orchestrator:8080` | The agent orchestrator, whose `POST /start` the agent tools call (`micronaut.http.services.agent-orchestrator.url`): by default the orchestrator container on the same Docker network; under Kubernetes its Service, `http://agent-orchestrator` |
| `AGENT_PROXY_SESSIONS_DIR`    | `/var/lib/agent-proxy/sessions` | One folder per session (`agent-proxy.sessions-dir`); put it on a persistent volume |
