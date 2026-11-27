# llm-watchdog

Micronaut 5 (Java 25) microservice, built as a GraalVM native image for Docker/Kubernetes. Every call Claude Code
makes to the LLM provider goes through it: agent-proxy points each Claude Code run at its LLM hop
(`ANTHROPIC_BASE_URL`), which forwards every call here with the session's JWT as a bearer token when the session has
one, and only this service has the provider's API key. Each call is analyzed before it is sent (`AnalyzeLlmRequestService`);
for now the analysis prints the call and lets it go on. A security analysis that rejects suspicious calls replaces it
later.

## API

| Method     | Path                | Description |
|------------|---------------------|-------------|
| POST, GET  | `/v1/...`           | The Anthropic Messages API as Claude Code calls it (`/v1/messages`, `/v1/messages/count_tokens`, ...). The call is analyzed, then forwarded to `ANTHROPIC_BASE_URL` with the path and query, the caller's headers except its own key (`x-api-key`, `Authorization`) and the hop's headers, and this service's `ANTHROPIC_API_KEY`. The provider's response, streamed (`text/event-stream`) or not, goes back as it comes, status and headers included. A rejected call never reaches the provider: **403** with an Anthropic API error body, `{"type": "error", "error": {"type": "permission_error", "message": "<reason>"}}` |
| GET        | `/health/liveness`  | Kubernetes liveness probe |
| GET        | `/health/readiness` | Kubernetes readiness probe |

The key a caller sends (`x-api-key`, or a bearer token) is no secret: agent-proxy gives each Claude Code run its
session id as the key, so every call is printed with the session that made it. Nothing checks it yet: whoever reaches
this service uses the provider's key, so keep it inside the cluster.

## Build & run

```bash
./mvnw -f ../agent-commons/parent install   # once, and after agent-commons changes: comagents-parent (the parent
                                     # pom of this service) and agent-commons, not published
./mvnw test                          # tests
./mvnw package && java -jar target/llm-watchdog-0.1.jar   # JVM

./build-image.sh                     # native image (no local GraalVM needed); the context is the comagents root,
                                     # so agent-commons is built inside the image
docker run -d --name llm-watchdog -p 8080:8080 \
  -e ANTHROPIC_BASE_URL=https://api.deepseek.com/anthropic \
  -e ANTHROPIC_API_KEY \
  llm-watchdog:0.1                   # key taken from the host env, not the command line

kubectl create secret generic llm-watchdog-secrets \
  --from-literal=anthropic-api-key="$ANTHROPIC_API_KEY"
kubectl apply -f k8s/deployment.yaml
```

The Docker image is a mostly-static native binary on `gcr.io/distroless/base-debian13:nonroot`, running as non-root.

## Configuration

| Env var                         | Default                     | Description |
|---------------------------------|-----------------------------|-------------|
| `MICRONAUT_SERVER_PORT`         | `8080`                      | HTTP port |
| `ANTHROPIC_BASE_URL`            | `https://api.anthropic.com` | The LLM provider: any Anthropic Messages API, e.g. DeepSeek's `https://api.deepseek.com/anthropic` (`llm-watchdog.anthropic-url`) |
| `ANTHROPIC_API_KEY`             | — (required)                | The provider's API key, added to every forwarded call (`llm-watchdog.anthropic-api-key`) |
| `LLM_WATCHDOG_READ_TIMEOUT`     | `10m`                       | How long a forwarded call may wait for the provider (`micronaut.http.services.anthropic.read-timeout`) |
| `LLM_WATCHDOG_MAX_REQUEST_SIZE` | `100MB`                     | The largest call accepted: a call carries the whole conversation so far, images and documents included (`micronaut.server.max-request-size`) |
