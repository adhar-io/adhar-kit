#!/usr/bin/env bash
# Integration harness for adhar-kit-dapr against a real Dapr sidecar (daprd 1.18) in Docker.
#
#   ./run-it.sh            start the sidecar, run DaprSidecarIntegrationTest, stop everything
#   ./run-it.sh up         start redis + daprd and leave them running (for debugging)
#   ./run-it.sh down       stop and remove them
#
# Ports (host): daprd HTTP 3540, gRPC 3541, the test application 3549. Containers are named
# adhar-kit-it-java-* so they never collide with the other kits' harnesses. The test itself
# starts and stops the same containers when they are not already running, so plain
#   ADHAR_KIT_DAPR_IT=1 mvn -o -B -Djacoco.skip=true -pl adhar-kit-dapr test -Dtest=DaprSidecarIntegrationTest
# works too.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "${HERE}/../../../.." && pwd)"
DAPRD_IMAGE="${DAPRD_IMAGE:-daprio/daprd:1.18.3}"
SCHEDULER_IMAGE="${SCHEDULER_IMAGE:-daprio/scheduler:1.18.3}"
REDIS_IMAGE="${REDIS_IMAGE:-redis:7-alpine}"
NET=adhar-kit-it-java-net
APP_ID=adhar-kit-it-java

up() {
  docker network inspect "${NET}" >/dev/null 2>&1 || docker network create "${NET}" >/dev/null
  docker run -d --rm --name adhar-kit-it-java-redis --network "${NET}" "${REDIS_IMAGE}" \
    redis-server --notify-keyspace-events KEA >/dev/null
  # The scheduler backs the Jobs API (and workflow/actor reminders).
  docker run -d --rm --name adhar-kit-it-java-scheduler --network "${NET}" "${SCHEDULER_IMAGE}" ./scheduler \
    --etcd-data-dir /tmp/etcd --port 50006 --enable-metrics=false --listen-address 0.0.0.0 \
    --override-broadcast-host-port adhar-kit-it-java-scheduler:50006 >/dev/null
  docker run -d --rm --name adhar-kit-it-java-daprd --network "${NET}" \
    -p 3540:3500 -p 3541:50001 \
    --add-host host.docker.internal:host-gateway \
    -v "${HERE}/components:/components:ro" -v "${HERE}/secrets:/secrets:ro" \
    "${DAPRD_IMAGE}" ./daprd \
      --app-id "${APP_ID}" --app-port 3549 --app-channel-address host.docker.internal \
      --dapr-http-port 3500 --dapr-grpc-port 50001 --resources-path /components \
      --scheduler-host-address adhar-kit-it-java-scheduler:50006 \
      --enable-metrics=false --log-level info >/dev/null
  for _ in $(seq 1 60); do
    if curl -fs -o /dev/null "http://127.0.0.1:3540/v1.0/healthz/outbound"; then
      echo "daprd ready on :3540 (gRPC :3541), app expected on :3549"
      return 0
    fi
    sleep 0.5
  done
  echo "daprd did not become ready" >&2
  docker logs adhar-kit-it-java-daprd >&2 || true
  return 1
}

down() {
  docker rm -f adhar-kit-it-java-daprd adhar-kit-it-java-scheduler adhar-kit-it-java-redis >/dev/null 2>&1 || true
  docker network rm "${NET}" >/dev/null 2>&1 || true
}

case "${1:-test}" in
  up) up ;;
  down) down ;;
  test)
    trap down EXIT
    up
    cd "${REPO}"
    ADHAR_KIT_DAPR_IT=1 ADHAR_KIT_DAPR_IT_EXTERNAL=1 \
      mvn -o -B -Djacoco.skip=true -pl adhar-kit-dapr test -Dtest=DaprSidecarIntegrationTest \
      -Dsurefire.failIfNoSpecifiedTests=false
    ;;
  *) echo "usage: $0 [test|up|down]" >&2; exit 2 ;;
esac
