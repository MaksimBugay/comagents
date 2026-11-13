#!/bin/sh
# Rebuilds the agent-proxy Docker image: stops and removes every agent-proxy container (including ones
# started from earlier builds), removes the old images, then builds a new agent-proxy:0.1.
# Plain POSIX sh, so it runs with both `sh build-image.sh` and `./build-image.sh`.
set -eu

cd "$(dirname "$0")"

NAME=agent-proxy
IMAGE="${NAME}:0.1"
# set by the Dockerfile on every build, so untagged builds left behind by a moved tag are found too
LABEL="com.comagents.app=${NAME}"

./remove-running-instances.sh

# ids are normalised to the 12-character short form so the same image is not removed twice
images=$(docker images -q --filter "label=${LABEL}" | cut -c1-12 | sort -u)
if [ -n "$images" ]; then
  echo "Removing old ${NAME} images:" $images
  # word splitting is intended: one argument per image id
  docker image rm -f $images >/dev/null
else
  echo "No ${NAME} images to remove"
fi

echo "Building ${IMAGE}"
# a new build id on every run forces a fresh install of the latest Claude Code (see Dockerfile)
docker build --build-arg CLAUDE_CODE_BUILD_ID="$(date +%s)" -t "$IMAGE" .

echo
echo "Built ${IMAGE}. Run it with:"
echo "  docker run -d --name ${NAME} -p 8080:8080 -v ${NAME}-sessions:/var/lib/agent-proxy/sessions \\"
echo "    -e CLONE_COMAGENTS_SOURCES_URL=... -e SOURCES_REPO_ACCESS_TOKEN -e ANTHROPIC_API_KEY -e PUSHCA_API_KEY ${IMAGE}"
