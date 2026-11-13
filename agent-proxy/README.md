# agent-proxy

Micronaut 5 (Java 25) microservice, built as a GraalVM native image for Docker/Kubernetes.

## API

| Method | Path                  | Description                     |
|--------|-----------------------|---------------------------------|
| GET    | `/{agentName}/info`   | Info about the given agent      |
| POST   | `/{agentName}/start`  | Set up an agent session (clone repo, copy agent files) and start the agent in the background: **202 Accepted**; **409** if the session id is already in use or still has data on disk |
| GET    | `/agent-status/{agentName}/{sessionId}` | Session state (`RUNNING`, `WAITING`, `DONE`, `FAILED`, `TIMED_OUT`, `KILLED`, `ARCHIVED`), times, and whether a response is available |
| GET    | `/agent-response/{agentName}/{sessionId}` | The agent's final answer (`body` base64); session files are deleted 5 minutes after the first fetch |
| GET    | `/health/liveness`    | Kubernetes liveness probe       |
| GET    | `/health/readiness`   | Kubernetes readiness probe      |

```console
$ curl localhost:8080/code_review/info
{"name":"code_review","proxy":"agent-proxy","version":"0.1","instance":"agent-proxy-7d9f-x2k"}
```

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
{"sessionId": "…", "agent": "CODE_REVIEW", "cloneSourceUrl": "https://github.com/<org>/<repo>.git",
 "actorAccountId": "Maks", "loggingLevel": "INFO"}
```

### Questions to the expert (`ask_expert`)

agent-proxy runs its own MCP server on `/mcp` (stateless HTTP). Every Claude Code run gets it with `--mcp-config`
(server `agentproxy`) and `mcp__agentproxy__ask_expert` pre-allowed. When the agent calls `ask_expert`, the question
is posted to the session chat as `Question: …`, the session is `WAITING`, and the next message written in the chat
is the tool's result. Without an answer within `AGENT_QUESTION_ANSWER_TIMEOUT`, or without a chat, the agent is
told to go on with its best judgement (later replies answer nothing). One question per session at a time.

Only the run itself can call the tool: its `--mcp-config` carries the session id and a random per-run token
(`X-Agent-Session-Id`, `X-Agent-Session-Token`), and other calls get an error result.

## Build & run

```bash
./mvnw test                          # tests
./mvnw package && java -jar target/agent-proxy-0.1.jar   # JVM

docker build -t agent-proxy:0.1 .    # native image (no local GraalVM needed)
docker run -d --name agent-proxy -p 8080:8080 \
  -v agent-proxy-sessions:/var/lib/agent-proxy/sessions \
  -e CLONE_COMAGENTS_SOURCES_URL=https://github.com/<org>/<repo>.git \
  -e SOURCES_REPO_ACCESS_TOKEN \
  -e ANTHROPIC_API_KEY \
  -e PUSHCA_API_KEY \
  agent-proxy:0.1                    # token and key taken from the host env, not the command line

kubectl create secret generic agent-proxy-secrets \
  --from-literal=sources-repo-access-token="$SOURCES_REPO_ACCESS_TOKEN" \
  --from-literal=anthropic-api-key="$ANTHROPIC_API_KEY" \
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
| `HOSTNAME`               | `local` | Reported `instance` (pod name in Kubernetes) |
| `CLONE_COMAGENTS_SOURCES_URL` | — (required) | Agent sources repository URL (`clone.comagents.sources.url`) |
| `SOURCES_REPO_ACCESS_TOKEN`   | — (required) | Access token for that repository (`sources.repo.access-token`) |
| `ANTHROPIC_API_KEY`           | — (required) | Claude API key for headless Claude Code (`anthropic.api-key`) |
| `PUSHCA_API_KEY`              | — (required) | API key of the organization registered in Pushca (`pushca.api-key`) |
| `PUSHCA_API_URL`              | `https://secure.fileshare.ovh/pushca` | Pushca server (`pushca.api-url`) |
| `PUSHCA_CHAT_URL`             | `https://secure.fileshare.ovh/chat-room.html` | Chat page; each new session chat is logged with its join link (`pushca.chat-url`) |
| `PUSHCA_CONNECT_TIMEOUT`      | `10s`        | How long signing in, opening the socket and waiting for the server to accept commands may each take (`pushca.connect-timeout`) |
| `AGENT_PROXY_MCP_URL`         | `http://127.0.0.1:8080/mcp` | Where Claude Code reaches agent-proxy's MCP server (`agent-proxy.mcp-url`); change it with the port |
| `AGENT_QUESTION_ANSWER_TIMEOUT` | `4m`       | How long `ask_expert` waits for an answer; below 5 min, after which Claude Code aborts the call (`agent-question.answer-timeout`) |
| `AGENT_PROXY_SESSIONS_DIR`    | `/var/lib/agent-proxy/sessions` | One folder per session (`agent-proxy.sessions-dir`); put it on a persistent volume |
