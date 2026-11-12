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

## 5. Remote Q&A not implemented

**Problem.** `spec/claude_q_and_a.md` (Pushca `HumanChannel`, `ask_human` MCP tool, `--permission-prompt-tool`,
`--resume` follow-ups) is designed but not built. Headless runs can't ask anyone; the kick-off prompt tells
Claude to assume and record open questions.

**Impact.** Not a defect; the next feature. When it's built, a session waiting for a human answer prints nothing,
so the idle timeout (`claude.session.idle-timeout`) must not count that wait (see `spec/claude_q_and_a.md`).

**Fix.** The implementation order in `spec/claude_q_and_a.md`, section 8. Its **verify** item about `allow` rules
from `--settings` is resolved: they don't apply in headless runs, so allowed tools now come from `agent.json`
(`--allowedTools`). That spec's command line and "Permissions in headless mode" section should be updated
accordingly.

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
