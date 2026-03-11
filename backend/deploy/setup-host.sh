#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ENV_FILE="${1:-$SCRIPT_DIR/hosts/core-frontend.env.example}"
ACTION="${2:-install}"
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

PUBLIC_IP="${APP_PUBLIC_HOST:?APP_PUBLIC_HOST is required}"

bind_ip() {
  if ip addr show lo | grep -q "${PUBLIC_IP}/32"; then
    echo "Loopback already contains ${PUBLIC_IP}/32"
    return
  fi
  ip addr add "${PUBLIC_IP}/32" dev lo
  echo "Bound ${PUBLIC_IP}/32 to lo"
}

remove_ip() {
  if ip addr show lo | grep -q "${PUBLIC_IP}/32"; then
    ip addr del "${PUBLIC_IP}/32" dev lo
    echo "Removed ${PUBLIC_IP}/32 from lo"
    return
  fi
  echo "Loopback does not contain ${PUBLIC_IP}/32"
}

install_service() {
  cat > "$INSTALL_SCRIPT_PATH" <<EOF
#!/usr/bin/env bash
set -euo pipefail
ip addr show lo | grep -q '${PUBLIC_IP}/32' || ip addr add '${PUBLIC_IP}/32' dev lo
EOF
  chmod 755 "$INSTALL_SCRIPT_PATH"

  cat > "$SERVICE_PATH" <<EOF
[Unit]
Description=Bind QForge public IP to loopback
After=network-online.target
Wants=network-online.target

[Service]
Type=oneshot
ExecStart=${INSTALL_SCRIPT_PATH}
RemainAfterExit=yes

[Install]
WantedBy=multi-user.target
EOF

  systemctl daemon-reload
  systemctl enable --now "$SERVICE_NAME"
  echo "Installed and started ${SERVICE_NAME}"
}

status() {
  ip addr show lo | grep -n "$PUBLIC_IP" || true
  systemctl status "$SERVICE_NAME" --no-pager || true
}

case "$ACTION" in
  apply)
    bind_ip
    ;;
  remove)
    remove_ip
    ;;
  install)
    bind_ip
    install_service
    ;;
  status)
    status
    ;;
  *)
    echo "Usage: $0 <env-file> [install|apply|remove|status]" >&2
    exit 1
    ;;
esac
