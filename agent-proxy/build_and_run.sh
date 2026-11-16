#!/bin/sh
# Rebuilds agent-proxy:0.1 (build-image.sh), starts it with DeepSeek's Anthropic-compatible API as the model
# provider, and follows its logs (Ctrl+C stops following; the container keeps running).
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

./build-image.sh

echo "Starting ${NAME}"
docker run -d --name "$NAME" -p 8087:8080 \
  -v "${NAME}-sessions:/var/lib/agent-proxy/sessions" \
  -e CLONE_COMAGENTS_SOURCES_URL=https://github.com/MaksimBugay/comagents.git \
  -e SOURCES_REPO_ACCESS_TOKEN \
  -e ANTHROPIC_BASE_URL=https://api.deepseek.com/anthropic \
  -e ANTHROPIC_API_KEY \
  -e CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC=1 \
  -e PUSHCA_API_KEY \
  "$IMAGE" >/dev/null

docker logs -f "$NAME"
