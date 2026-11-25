#!/bin/sh
# Rebuilds agent-proxy:0.1 (build-image.sh) and agent-orchestrator:0.1 (../agent-orchestrator/build-image.sh), starts
# agent-proxy with DeepSeek's Anthropic-compatible API as the model provider and agent-orchestrator with the same
# Pushca key, and follows agent-proxy's logs (Ctrl+C stops following; the containers keep running). Both run on the
# Docker network comagents, where agent-proxy reaches the orchestrator (for its agent tools) by its container name, at
# its default AGENT_PROXY_ORCHESTRATOR_URL http://agent-orchestrator:8080.
#
# Usage: ./build_and_run.sh <sources repo access token> <anthropic api key> <pushca api key>
#
# The secrets are handed to docker as environment variables by name (-e NAME), so they do not show up in the
# docker command line; they are still visible in `docker inspect` of the container.
set -eu

cd "$(dirname "$0")"

if [ $# -ne 3 ]; then
  echo "Usage: $0 <sources repo access token> <anthropic api key> <pushca api key>" >&2
  exit 1
fi

SOURCES_REPO_ACCESS_TOKEN=$1
ANTHROPIC_API_KEY=$2
PUSHCA_API_KEY=$3
export SOURCES_REPO_ACCESS_TOKEN ANTHROPIC_API_KEY PUSHCA_API_KEY

NAME=agent-proxy
IMAGE="${NAME}:0.1"
ORCHESTRATOR_NAME=agent-orchestrator
ORCHESTRATOR_IMAGE="${ORCHESTRATOR_NAME}:0.1"
NETWORK=comagents

./build-image.sh
../agent-orchestrator/build-image.sh

docker network inspect "$NETWORK" >/dev/null 2>&1 || docker network create "$NETWORK" >/dev/null

echo "Starting ${NAME}"
docker run -d --name "$NAME" --network "$NETWORK" -p 8087:8080 \
  -v "${NAME}-sessions:/var/lib/agent-proxy/sessions" \
  -e CLONE_COMAGENTS_SOURCES_URL=https://github.com/MaksimBugay/comagents.git \
  -e SOURCES_REPO_ACCESS_TOKEN \
  -e ANTHROPIC_BASE_URL=https://api.deepseek.com/anthropic \
  -e ANTHROPIC_API_KEY \
  -e CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC=1 \
  -e PUSHCA_API_KEY \
  "$IMAGE" >/dev/null

echo "Starting ${ORCHESTRATOR_NAME}"
docker run -d --name "$ORCHESTRATOR_NAME" --network "$NETWORK" -p 8088:8080 \
  -e PUSHCA_API_KEY \
  "$ORCHESTRATOR_IMAGE" >/dev/null

docker logs -f "$NAME"
