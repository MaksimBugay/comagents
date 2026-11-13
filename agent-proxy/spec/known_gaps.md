# Known gaps

Status as of 2026-11-12. What works: `POST /{agentName}/start` (202) → `GET /agent-status/...` →
`GET /agent-response/...`, headless Claude Code runs with per-agent `agent.json` (model, effort, allowed tools,
kick-off prompt), scoped web access, and stream-json logging. The gaps below are ordered by priority.

## 1. Sessions that are never fetched are never cleaned up

**Problem.** Cleanup is scheduled only by the first *successful* `GET /agent-response/...`
(`AgentResponseService`). A session that ends `FAILED`, `TIMED_OUT` or `KILLED` has no response to fetch, and a
client may never fetch a successful one. Their session folder (`<sessions-dir>/<session id>`: cloned repository, agent
files, transcript) stays on disk and their `ClaudeAgentSession` stays in the registry's memory for ever.

**Impact.** Disk and memory grow with every unfetched session; over time the container fills up.

**Fix.** Also schedule cleanup a fixed time after a session finishes (e.g. `agent-session.retention=1h`), whether
or not the response was fetched: from `ClaudeAgentSessionRegistry.onSessionFinishedHandler`, through the same
`AgentSetupService.cleanUp`. Keep the shorter 5-minute delay after the first fetch.

## 2. No cost cap per run

**Problem.** A run is bounded only by `claude.session.timeout` (60 min). Claude Code has `--max-budget-usd`
(print mode only), which agent-proxy doesn't pass.

**Impact.** A runaway session (e.g. a huge repository) can cost far more than a typical review ($0.5–1.5).

**Fix.** Optional `maxBudgetUsd` in `agent.json` (`ClaudeAgentConfig`), passed as `--max-budget-usd`.

**Caveat: non-Anthropic models.** Claude Code prices every run with its built-in Anthropic price table, also when
`ANTHROPIC_BASE_URL` points at another provider. Token counts are right, the dollar amount is not: a DeepSeek
(`deepseek-flash`) review reported `total_cost_usd` $1.39 in its `result` event, while DeepSeek billed about $0.10
(~14× overstated). `--max-budget-usd` uses the same figure, so a $1 cap would stop a DeepSeek run at about $0.07 of
real spend. For agents on other providers, either scale the cap by the provider's price ratio, or cap tokens instead
of dollars (from the `usage` of the stream-json events). Real spend is in the provider's console.

## 3. Single instance only: multiple replicas and restarts

**Problem.** Session folders are on persistent storage (`agent-proxy.sessions-dir`: a Docker named volume, or a
`PersistentVolumeClaim` in Kubernetes), but running sessions live in one pod's memory (`ClaudeAgentSessionRegistry`)
and pending cleanups in `AgentResponseService`'s memory. `k8s/deployment.yaml` therefore runs `replicas: 1`.

**Impact.**
- Can't scale out: with more replicas, `GET /agent-status` or `/agent-response` can reach a pod that doesn't run
  the session and return 404.
- A restart loses running sessions (Claude processes die with the pod) and scheduled cleanups. Archived sessions
  and responses survive (`resume` and `/agent-response` read them from disk), but `/agent-status` only knows
  sessions in memory, and the folders of sessions that were running stay on the volume. With persistent storage
  nothing deletes them any more, which makes gap 1 more important.

**Fix.** Session affinity on the session id would allow more replicas without other changes. Long term: one Kubernetes Job (or container) per session with shared storage for responses, and the session
state in a store instead of memory. Meanwhile: archive running sessions on shutdown instead of killing them (a
normal stop only), fall back to `session-state.json` for `/agent-status`, and delete leftover session folders by
age.

## 4. No isolation between sessions

**Problem.** All Claude Code runs are child processes of agent-proxy, running as the same user (`nonroot`) in
the same container, sharing the sessions dir (each session's Claude Code state is in its own session folder).

**Impact.** Fine for the read-only `code_review` agent (shell denied, edits denied). An agent allowed to run
shell commands or edit files could read or change another session's folder.
Claude Code's own Bash sandbox needs `enableWeakerNestedSandbox` inside Docker, which its docs say considerably
weakens it.

**Fix.** Before adding agents that run commands or write files: one container (or Kubernetes Job) per session.
Restricting edits with `Edit(//var/lib/agent-proxy/sessions/<session id>/**)` covers the file tools but not shell
commands.

## 5. Remote Q&A: what is left

**Problem.** `ask_expert` works through the session chat: a quick answer is the tool's result; without one in
`agent-question.answer-timeout` the session is archived and the next chat message resumes it. Not built: approving
actions remotely (`--permission-prompt-tool`), sending new instructions to a session (`--resume` follow-ups), an
expiry for questions nobody answers, and persisting the question before the run is archived (a restart in those
seconds loses it). A session waiting for an answer reports `ARCHIVED`, not `WAITING`.

**Impact.** Unanswered sessions stay on disk forever (see gap 1); callers can't tell "waiting for the expert" from
"archived on request".

**Fix.** Build the remaining pieces when needed; add an answer TTL with the cleanup of gap 1.

## 6. Smaller items

- **Exposed GitHub token.** The token once committed in `application.properties` (as a comment) must be rotated
  if it hasn't been.
- **`AgentProxyConf.getAnthropicApiKey()` is unused.** Claude Code reads `ANTHROPIC_API_KEY` from the inherited
  environment. Remove it, or use it to pass the key only to the Claude process.
- **`createdAt` of a response** is the file's creation time where the filesystem records it, otherwise its
  last-modified time (same moment, since the file is written once).
- **Web access is limited by design.** `code_review` may fetch only `github.com`, `osv.dev`, `nvd.nist.gov` and
  Maven Central; other fetches are refused and reported as unverified. Kept on purpose: allowing any domain would
  let instructions hidden in a reviewed repository send data to arbitrary sites.
