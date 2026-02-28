#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

LOG_DIR="$ROOT_DIR/.dev-logs"
mkdir -p "$LOG_DIR"

pkill -f "AuthServiceApplication" >/dev/null 2>&1 || true
pkill -f "QForgeGatewayApplication" >/dev/null 2>&1 || true
pkill -f "spring-boot:run.*auth-service" >/dev/null 2>&1 || true
pkill -f "spring-boot:run.*gateway-service" >/dev/null 2>&1 || true

echo "[dev] installing shared module"
mvn -DskipTests -pl libs/common-contract -am install

echo "[dev] starting auth-service on :8088"
mvn -DskipTests -pl services/auth-service spring-boot:run >"$LOG_DIR/auth-service.log" 2>&1 &
AUTH_PID=$!
echo "$AUTH_PID" >"$LOG_DIR/auth-service.pid"

sleep 5

echo "[dev] starting gateway-service on :8080"
mvn -DskipTests -pl services/gateway-service spring-boot:run >"$LOG_DIR/gateway-service.log" 2>&1 &
GATEWAY_PID=$!
echo "$GATEWAY_PID" >"$LOG_DIR/gateway-service.pid"

cleanup() {
  kill "$AUTH_PID" "$GATEWAY_PID" >/dev/null 2>&1 || true
}
trap cleanup INT TERM

echo "[dev] auth log: $LOG_DIR/auth-service.log"
echo "[dev] gateway log: $LOG_DIR/gateway-service.log"
wait -n "$AUTH_PID" "$GATEWAY_PID"
exit 1
