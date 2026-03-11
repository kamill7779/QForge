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
DEFAULT_SECRETS_FILE="$SCRIPT_DIR/remote-stack.secrets.local.env"
SECRETS_FILE="${QFORGE_DEPLOY_SECRETS_FILE:-$DEFAULT_SECRETS_FILE}"
if [[ -f "$SECRETS_FILE" ]]; then
  . "$SECRETS_FILE"
fi
set +a

export COMPOSE_PROFILES="${COMPOSE_PROFILES:?COMPOSE_PROFILES is required}"
export APP_NODE_IP="${APP_NODE_IP:?APP_NODE_IP is required}"
COMPOSE_FILE="$SCRIPT_DIR/docker-compose.remote.yml"

if [[ $# -eq 0 ]]; then
  set -- up -d --build
fi

echo "Using env: $ENV_FILE"
if [[ -f "$SECRETS_FILE" ]]; then
  echo "Loaded secrets: $SECRETS_FILE"
fi
echo "Profiles: $COMPOSE_PROFILES"
echo "Node IP: $APP_NODE_IP"
echo "Compose file: $COMPOSE_FILE"

docker compose -f "$COMPOSE_FILE" "$@"
