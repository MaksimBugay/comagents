# agent-orchestrator

Micronaut 5 (Java 25) microservice, built as a GraalVM native image for Docker/Kubernetes. It talks to the agent
executors (agent-proxy) over Pushca.

## API

| Method | Path                  | Description                     |
|--------|-----------------------|---------------------------------|
| POST   | `/start`              | Start a session of the body's `agent` (`agentContext`, `agentCallSequence`, `actorAccountId`, `agentResponseWaiterDeviceId`, `loggingLevel`; the started agent is appended to `agentCallSequence`) with a new session id on the discovered executor of the agent's harness with most free slots: **202 Accepted** with `{"sessionId": …}`. **400** for an invalid request or agent context, **429** when no discovered executor of the agent's harness has a free slot, or the executor has none after all (try again: it goes to another), **501** when the executor has no setup service for the agent, **409** when the session id is in use; **504** when the executor didn't reply within `AGENT_ORCHESTRATOR_RESPONSE_TIMEOUT` |
| GET    | `/agent-response/{sessionId}` | The session's response, `{"sessionId": …, "agentContext": …, "createdAt": …}`: the agent's final answer as an agent context (per the agent's `responseSchema`, or one `STRING` item `response`), from the `AgentSessionResponse` its executor sent (kept 5 minutes), else asked from the executor that runs it. **404** with `AgentResponseUnavailable`: with the session's `state` when the response isn't ready yet (try again), without one when the session is unknown; **504** when the session's executor didn't reply within `AGENT_ORCHESTRATOR_RESPONSE_TIMEOUT` |
| GET    | `/health/liveness`    | Kubernetes liveness probe       |
| GET    | `/health/readiness`   | Kubernetes readiness probe      |

### Over Pushca

- **The orchestrator's own client:** signed in to workspace `comagents-main` as `agent-orchestrator@comagents.com`,
  application `AGENT_ORCHESTRATOR`, device id = `agent-orchestrator.device-id`. It is opened on startup and kept open
  by the Pushca client itself, which signs in or reconnects every 2 s while it isn't open. A `PUSHCA_API_KEY` that
  Pushca refuses (HTTP 401/403) stops the startup.
- **Incoming:** every `ComagentsMessage` from the executors (discovery `AgentInfoResponse`s, replies, agent responses) is
  passed to the `ComagentsMessageListener`s of its payload class, each on its own thread. Every `AgentInfoResponse` (discovery
  report or start reply) updates the `AgentDiscoveryRegistry` (`AgentInfoListener`), which keeps each executor until
  `AGENT_ORCHESTRATOR_DISCOVERY_TTL` passes without a fresher one. A session started here is kept with its executor
  (`RunningAgentSessionRegistry`) until its `AgentSessionResponse` comes (`AgentSessionResponseListener`), or for
  `AGENT_ORCHESTRATOR_RUNNING_SESSION_TTL`.
- **Errors:** an executor's error reply becomes the executor's exception again, when it is known here with a
  `(String)` constructor (the agent-commons exceptions), so the HTTP API answers it with the status it maps that exception to.
- **Outgoing:** every message to an executor goes through one queue and over the one connection, one by one. A send
  that finds the connection broken is retried every second until `AGENT_ORCHESTRATOR_MESSAGE_TIMEOUT` (30 s) has
  passed since it was queued; a message not sent by then is dropped with an error logged.

## Build & run

```bash
./mvnw -f ../agent-commons/pom.xml install   # once, and after agent-commons changes: it is not published
./mvnw test                          # tests
./mvnw package && java -jar target/agent-orchestrator-0.1.jar   # JVM

./build-image.sh                     # native image (no local GraalVM needed); the context is the comagents root,
                                     # so agent-commons is built inside the image
docker run -d --name agent-orchestrator -p 8080:8080 \
  -e PUSHCA_API_KEY \
  agent-orchestrator:0.1             # key taken from the host env, not the command line

kubectl create secret generic agent-orchestrator-secrets \
  --from-literal=pushca-api-key="$PUSHCA_API_KEY"
kubectl apply -f k8s/deployment.yaml
```

The Docker image is a mostly-static native binary on `gcr.io/distroless/base-debian13:nonroot`, running as non-root.

## Configuration

| Env var                  | Default | Description            |
|--------------------------|---------|------------------------|
| `MICRONAUT_SERVER_PORT`  | `8080`  | HTTP port              |
| `AGENT_ORCHESTRATOR_VERSION`   | `0.1` | Version (`agent-orchestrator.version`) |
| `AGENT_ORCHESTRATOR_DEVICE_ID` | —     | Pushca device id (`agent-orchestrator.device-id`); when not set: the Docker container id, or the Kubernetes pod UID or `HOSTNAME` (`local` without it) with a random suffix, so every instance is a device of its own; a set one must be unique in the cluster |
| `PUSHCA_API_KEY`               | — (required) | API key of the organization registered in Pushca (`pushca.api-key`); a key Pushca refuses stops the startup |
| `PUSHCA_API_URL`               | `https://secure.fileshare.ovh/pushca` | Pushca server (`pushca.api-url`) |
| `PUSHCA_CONNECT_TIMEOUT`       | `10s` | How long signing in, opening the socket and waiting for the server to accept commands may each take (`pushca.connect-timeout`) |
| `AGENT_ORCHESTRATOR_MESSAGE_TIMEOUT` | `30s` | How long a message for an executor may take to be sent over Pushca; sends finding the connection broken are retried every second until then, and a message not sent by then is dropped with an error logged (`agent-orchestrator.message-timeout`) |
| `AGENT_ORCHESTRATOR_DISCOVERY_TTL` | `10s` | How long a discovered agent executor stays available without a fresh `AgentInfoResponse`; executors report every 5 s, so the default misses one report at most (`agent-orchestrator.discovery-ttl`) |
| `AGENT_ORCHESTRATOR_RESPONSE_TIMEOUT` | `30s` | How long a request to an executor waits for its response, sending included; then it fails with a timeout (`agent-orchestrator.response-timeout`) |
| `AGENT_ORCHESTRATOR_ONE_SESSION_PER_EXECUTOR` | `false` | Start a session only on an executor with nothing on it: no running, waiting or archived sessions, and no finished ones whose data (repository clone, transcript, response) is still on disk. Each session then has its executor's container to itself; give executors one slot (`CLAUDE_SESSION_MAX_CONCURRENT=1`) and enough of them (`agent-orchestrator.one-session-per-executor`) |
| `AGENT_ORCHESTRATOR_RUNNING_SESSION_TTL` | `60m` | How long a session started by this orchestrator stays known; the default is an executor's longest session run, `CLAUDE_SESSION_TIMEOUT` (`agent-orchestrator.running-session-ttl`) |
