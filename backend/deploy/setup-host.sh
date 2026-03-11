#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ENV_FILE="${1:-$SCRIPT_DIR/hosts/core-frontend.env.example}"
ACTION="${2:-cleanup}"
SERVICE_NAME="qforge-loopback-ip.service"
INSTALL_SCRIPT_PATH="/usr/local/bin/qforge-bind-loopback-ip.sh"
SERVICE_PATH="/etc/systemd/system/${SERVICE_NAME}"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Env file not found: $ENV_FILE" >&2
  exit 1
fi

if [[ "$EUID" -ne 0 ]]; then
  echo "Please run as root or with sudo." >&2
  exit 1
fi

set -a
. "$ENV_FILE"
set +a

LEGACY_IP="${LEGACY_LOOPBACK_IP:-${APP_PUBLIC_HOST:-}}"

cleanup() {
  if systemctl list-unit-files | grep -q "^${SERVICE_NAME}"; then
    systemctl disable --now "$SERVICE_NAME" || true
  fi

  rm -f "$SERVICE_PATH" "$INSTALL_SCRIPT_PATH"
  systemctl daemon-reload

  if [[ -n "$LEGACY_IP" ]] && ip addr show lo | grep -q "${LEGACY_IP}/32"; then
    ip addr del "${LEGACY_IP}/32" dev lo
    echo "Removed legacy loopback IP ${LEGACY_IP}/32"
  else
    echo "No legacy loopback IP found on lo"
  fi

  echo "Legacy loopback cleanup complete"
}

status() {
  if [[ -n "$LEGACY_IP" ]]; then
    ip addr show lo | grep -n "$LEGACY_IP" || true
  else
    ip -4 addr show lo
  fi
  systemctl status "$SERVICE_NAME" --no-pager || true
}

case "$ACTION" in
  cleanup)
    cleanup
    ;;
  status)
    status
    ;;
  *)
    echo "Usage: $0 <env-file> [cleanup|status]" >&2
    exit 1
    ;;
esac
