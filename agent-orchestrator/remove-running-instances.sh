#!/bin/sh
# Stops and removes every agent-orchestrator container (including ones started from earlier builds).
# Images are kept, so a new container can be started right away.
# Plain POSIX sh, so it runs with both `sh remove-running-instances.sh` and `./remove-running-instances.sh`.
set -eu

NAME=agent-orchestrator

# A container keeps the image name it was created from (Config.Image) even after that tag moves to a
# newer build, so match on it rather than on `--filter ancestor`, which only sees the current build.
containers=""
for id in $(docker ps -aq); do
  config_image=$(docker inspect --format '{{.Config.Image}}' "$id")
  case "$config_image" in
    "$NAME" | "$NAME":*)
      containers="$containers $id"
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
