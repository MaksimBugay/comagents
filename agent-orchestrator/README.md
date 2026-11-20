# agent-orchestrator

Micronaut 5 (Java 25) microservice, built as a GraalVM native image for Docker/Kubernetes. It talks to the agent
executors (agent-proxy) over Pushca.

## API

| Method | Path                  | Description                     |
|--------|-----------------------|---------------------------------|
| GET    | `/health/liveness`    | Kubernetes liveness probe       |
| GET    | `/health/readiness`   | Kubernetes readiness probe      |

### Over Pushca

- **The orchestrator's own client:** signed in to workspace `comagents-main` as `agent-orchestrator@comagents.com`,
  application `AGENT_ORCHESTRATOR`, device id = `agent-orchestrator.device-id`. It is opened on startup and kept open
  by the Pushca client itself, which signs in or reconnects every 2 s while it isn't open. A `PUSHCA_API_KEY` that
  Pushca refuses (HTTP 401/403) stops the startup.
- **Incoming:** every `ComagentsMessage` from the executors (discovery `AgentInfo`s, replies, agent responses) is
  passed to the `ComagentsMessageListener`s of its payload class, each on its own thread. Every `AgentInfo` (discovery
  report or start reply) updates the `AgentDiscoveryRegistry` (`AgentInfoListener`), which keeps each executor until
  `AGENT_ORCHESTRATOR_DISCOVERY_TTL` passes without a fresher one.
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
| `AGENT_ORCHESTRATOR_DEVICE_ID` | —     | Pushca device id (`agent-orchestrator.device-id`); when not set: the Docker container id, the Kubernetes pod UID, or `HOSTNAME` (`local` without it) |
| `PUSHCA_API_KEY`               | — (required) | API key of the organization registered in Pushca (`pushca.api-key`); a key Pushca refuses stops the startup |
| `PUSHCA_API_URL`               | `https://secure.fileshare.ovh/pushca` | Pushca server (`pushca.api-url`) |
| `PUSHCA_CONNECT_TIMEOUT`       | `10s` | How long signing in, opening the socket and waiting for the server to accept commands may each take (`pushca.connect-timeout`) |
| `AGENT_ORCHESTRATOR_MESSAGE_TIMEOUT` | `30s` | How long a message for an executor may take to be sent over Pushca; sends finding the connection broken are retried every second until then, and a message not sent by then is dropped with an error logged (`agent-orchestrator.message-timeout`) |
| `AGENT_ORCHESTRATOR_DISCOVERY_TTL` | `10s` | How long a discovered agent executor stays available without a fresh `AgentInfo`; executors report every 5 s, so the default misses one report at most (`agent-orchestrator.discovery-ttl`) |
| `AGENT_ORCHESTRATOR_RESPONSE_TIMEOUT` | `30s` | How long a request to an executor waits for its response, sending included; then it fails with a timeout (`agent-orchestrator.response-timeout`) |
