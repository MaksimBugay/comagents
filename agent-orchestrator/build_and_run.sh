#!/bin/sh
# Rebuilds agent-orchestrator:0.1 (build-image.sh), starts it and follows its logs (Ctrl+C stops following; the
# container keeps running).
#
# Usage: ./build_and_run.sh <pushca api key>
#
# The secret is handed to docker as an environment variable by name (-e NAME), so it does not show up in the docker
# command line; it is still visible in `docker inspect` of the container.
set -eu

cd "$(dirname "$0")"

if [ $# -ne 1 ]; then
  echo "Usage: $0 <pushca api key>" >&2
  exit 1
fi

PUSHCA_API_KEY=$1
export PUSHCA_API_KEY

NAME=agent-orchestrator
IMAGE="${NAME}:0.1"

./build-image.sh

echo "Starting ${NAME}"
docker run -d --name "$NAME" -p 8088:8080 \
  -e PUSHCA_API_KEY \
  "$IMAGE" >/dev/null

docker logs -f "$NAME"
