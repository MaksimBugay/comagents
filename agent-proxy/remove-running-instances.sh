#!/bin/sh
# Stops and removes every agent-proxy container (including ones started from earlier builds). Images and volumes
# are kept, so the sessions in the agent-proxy-sessions volume survive and a new container can be started right away.
# Plain POSIX sh, so it runs with both `sh remove-running-instances.sh` and `./remove-running-instances.sh`.
set -eu

NAME=agent-proxy

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
