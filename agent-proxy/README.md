# agent-proxy

Micronaut 5 (Java 25) microservice, built as a GraalVM native image for Docker/Kubernetes.

## API

| Method | Path                  | Description                     |
|--------|-----------------------|---------------------------------|
| GET    | `/{agentName}/info`   | Info about the given agent      |
| POST   | `/{agentName}/start`  | Set up an agent session (clone repo, copy agent files) and start the agent in the background: **202 Accepted** |
| GET    | `/agent-status/{agentName}/{sessionId}` | Session state (`RUNNING`, `DONE`, `FAILED`, `TIMED_OUT`, `KILLED`), times, and whether a response is available |
| GET    | `/agent-response/{agentName}/{sessionId}` | The agent's final answer (`body` base64); session files are deleted 5 minutes after the first fetch |
| GET    | `/health/liveness`    | Kubernetes liveness probe       |
| GET    | `/health/readiness`   | Kubernetes readiness probe      |

```console
$ curl localhost:8080/code_review/info
{"name":"code_review","proxy":"agent-proxy","version":"0.1","instance":"agent-proxy-7d9f-x2k"}
```

## Build & run

```bash
./mvnw test                          # tests
./mvnw package && java -jar target/agent-proxy-0.1.jar   # JVM

docker build -t agent-proxy:0.1 .    # native image (no local GraalVM needed)
docker run --rm -p 8080:8080 \
  -e CLONE_COMAGENTS_SOURCES_URL=https://github.com/<org>/<repo>.git \
  -e SOURCES_REPO_ACCESS_TOKEN \
  -e ANTHROPIC_API_KEY \
  agent-proxy:0.1                    # token and key taken from the host env, not the command line

kubectl create secret generic agent-proxy-secrets \
  --from-literal=sources-repo-access-token="$SOURCES_REPO_ACCESS_TOKEN" \
  --from-literal=anthropic-api-key="$ANTHROPIC_API_KEY"
kubectl apply -f k8s/deployment.yaml   # one replica; /tmp and Claude Code's state on emptyDir volumes
```

The Docker image is a mostly-static native binary (starts in ~10 ms, ~20 MiB RSS) on `debian:trixie-slim`,
running as non-root, with the latest [Claude Code](https://code.claude.com/docs) CLI installed for headless
use (`claude -p "<prompt>"`). `./build-image.sh` reinstalls the latest Claude Code release on every build.

## Configuration

| Env var                  | Default | Description            |
|--------------------------|---------|------------------------|
| `MICRONAUT_SERVER_PORT`  | `8080`  | HTTP port              |
| `AGENT_PROXY_VERSION`    | `0.1`   | Reported `version`     |
| `HOSTNAME`               | `local` | Reported `instance` (pod name in Kubernetes) |
| `CLONE_COMAGENTS_SOURCES_URL` | — (required) | Agent sources repository URL (`clone.comagents.sources.url`) |
| `SOURCES_REPO_ACCESS_TOKEN`   | — (required) | Access token for that repository (`sources.repo.access-token`) |
| `ANTHROPIC_API_KEY`           | — (required) | Claude API key for headless Claude Code (`anthropic.api-key`) |
