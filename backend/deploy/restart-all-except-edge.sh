#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ENV_FILE="${1:-$SCRIPT_DIR/hosts/all-in-one-111.229.17.125-no-edge.env}"

SERVICES=(
  mysql
  redis
  rabbitmq
  nacos
  question-core-service
  question-basket-service
  exam-service
  exam-parse-service
  ocr-service
  persist-service
  gaokao-corpus-service
  gaokao-analysis-service
  qdrant
  export-sidecar
  web-exam
  gaokao-web
)

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Env file not found: $ENV_FILE" >&2
  exit 1
fi

set -a
. "$ENV_FILE"
set +a

echo "[restart] env: $ENV_FILE"
echo "[restart] stopping current remote stack containers if present"
bash "$SCRIPT_DIR/deploy.sh" "$ENV_FILE" down --remove-orphans

echo "[restart] starting all services except gateway-service and auth-service"
bash "$SCRIPT_DIR/deploy.sh" "$ENV_FILE" up -d --build "${SERVICES[@]}"

echo "[restart] started services: ${SERVICES[*]}"
echo "[restart] note: web-exam and gaokao-web will start, but their /api proxy still points to ${SELF_PUBLIC_IP:-the configured gateway host}:8080; without gateway-service, frontend API calls will fail as expected"