# agent-proxy

Micronaut 5 (Java 25) microservice, built as a GraalVM native image for Docker/Kubernetes.

## API

| Method | Path                  | Description                     |
|--------|-----------------------|---------------------------------|
| GET    | `/{agentName}/info`   | Info about the given agent      |
| POST   | `/{agentName}/start`  | Set up an agent session (clone repo, copy agent files) |
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
  agent-proxy:0.1                    # token taken from the host env, not the command line

kubectl apply -f k8s/deployment.yaml
```

The Docker image is a mostly-static native binary on `distroless/base-debian13:nonroot`
(starts in ~10 ms, ~20 MiB RSS), running as non-root with a read-only root filesystem.

## Configuration

| Env var                  | Default | Description            |
|--------------------------|---------|------------------------|
| `MICRONAUT_SERVER_PORT`  | `8080`  | HTTP port              |
| `AGENT_PROXY_VERSION`    | `0.1`   | Reported `version`     |
| `HOSTNAME`               | `local` | Reported `instance` (pod name in Kubernetes) |
| `CLONE_COMAGENTS_SOURCES_URL` | — (required) | Agent sources repository URL (`clone.comagents.sources.url`) |
| `SOURCES_REPO_ACCESS_TOKEN`   | — (required) | Access token for that repository (`sources.repo.access-token`) |
