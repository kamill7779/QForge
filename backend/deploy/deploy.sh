#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ENV_FILE="${1:-$SCRIPT_DIR/hosts/core-frontend.env.example}"
shift || true

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Env file not found: $ENV_FILE" >&2
  exit 1
fi

set -a
. "$ENV_FILE"
set +a

export COMPOSE_PROFILES="${COMPOSE_PROFILES:?COMPOSE_PROFILES is required}"
COMPOSE_FILE="$SCRIPT_DIR/docker-compose.remote.yml"

if [[ $# -eq 0 ]]; then
  set -- up -d --build
fi

if [[ "${SKIP_LOOPBACK_CHECK:-false}" != "true" ]] && ! ip addr show lo | grep -q "${APP_PUBLIC_HOST}/32"; then
  echo "Loopback does not contain ${APP_PUBLIC_HOST}/32. Run sudo ./setup-host.sh $ENV_FILE first." >&2
  exit 1
fi

echo "Using env: $ENV_FILE"
echo "Profiles: $COMPOSE_PROFILES"
echo "Compose file: $COMPOSE_FILE"

docker compose -f "$COMPOSE_FILE" "$@"
