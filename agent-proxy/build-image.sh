#!/bin/sh
# Rebuilds the agent-proxy Docker image: stops and removes every agent-proxy container (including ones
# started from earlier builds), removes the old images, then builds a new agent-proxy:0.1.
# Plain POSIX sh, so it runs with both `sh build-image.sh` and `./build-image.sh`.
set -eu

cd "$(dirname "$0")"

NAME=agent-proxy
IMAGE="${NAME}:0.1"

# A container keeps the image name it was created from (Config.Image) even after that tag moves to a
# newer build, so match on it rather than on `--filter ancestor`, which only sees the current build.
containers=""
old_images=""
for id in $(docker ps -aq); do
  info=$(docker inspect --format '{{.Config.Image}} {{.Image}}' "$id")
  config_image=${info%% *}
  image_id=${info#* }
  case "$config_image" in
    "$NAME" | "$NAME":*)
      containers="$containers $id"
      old_images="$old_images $image_id"
      ;;
  esac
done

if [ -n "$containers" ]; then
  echo "Stopping and removing ${NAME} containers:$containers"
  # word splitting is intended: one argument per container id
  docker rm -f $containers >/dev/null
else
  echo "No ${NAME} containers to remove"
fi

# tagged images of this repository plus the untagged builds the removed containers were running;
# ids are normalised to the 12-character short form so the same image is not removed twice
old_images="$old_images $(docker images -q "$NAME")"
images=$(printf '%s\n' $old_images | sed 's/^sha256://' | cut -c1-12 | sort -u)
if [ -n "$images" ]; then
  echo "Removing old ${NAME} images:" $images
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
echo "    -e CLONE_COMAGENTS_SOURCES_URL=... -e SOURCES_REPO_ACCESS_TOKEN -e ANTHROPIC_API_KEY ${IMAGE}"
