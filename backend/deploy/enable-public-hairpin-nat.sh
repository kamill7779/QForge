#!/usr/bin/env bash
set -euo pipefail

# Legacy helper for the retired public-IP hairpin NAT deployment path.
# The default remote deployment now uses private-IP registration and does not require this script.

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ENV_FILE="${1:-$SCRIPT_DIR/remote-stack.env}"

if [[ -f "$ENV_FILE" ]]; then
  set -a
  . "$ENV_FILE"
  set +a
fi

NETWORK_NAME="${DOCKER_NETWORK_NAME:-qforge-remote-net}"
PUBLIC_IP="${APP_PUBLIC_HOST:?APP_PUBLIC_HOST is required}"
LOCAL_IP="${APP_LOCAL_IP:-}"

if [[ -z "$LOCAL_IP" ]]; then
  LOCAL_IP="$(ip -4 route get 1.1.1.1 | awk '{for (i = 1; i <= NF; i++) if ($i == "src") { print $(i + 1); exit }}')"
fi

resolve_network_name() {
  local candidates=()
  local candidate
  local containers

  while IFS= read -r candidate; do
    [[ -n "$candidate" ]] || continue
    candidates+=("$candidate")
  done < <(docker network ls --format '{{.Name}}' \
    | awk -v target="$NETWORK_NAME" '$0 == target || $0 ~ ("(^|_)" target "$") { print }')

  if [[ "${#candidates[@]}" -eq 0 ]]; then
    return 1
  fi

  for candidate in "${candidates[@]}"; do
    containers="$(docker network inspect "$candidate" --format '{{len .Containers}}' 2>/dev/null || echo 0)"
    if [[ "$containers" != "0" ]]; then
      echo "$candidate"
      return 0
    fi
  done

  echo "${candidates[0]}"
}

delete_matching_rules() {
  local table="$1"
  local chain="$2"
  local pattern="$3"
  while IFS= read -r rule; do
    [[ "$rule" =~ $pattern ]] || continue
    read -r -a args <<< "${rule/-A /-D }"
    iptables -t "$table" "${args[@]}"
  done < <(iptables -t "$table" -S "$chain")
}

ensure_chain() {
  local chain="$1"
  iptables -t nat -N "$chain" 2>/dev/null || true
  iptables -t nat -F "$chain"
}

RESOLVED_NETWORK_NAME="$(resolve_network_name)"
if [[ -z "$RESOLVED_NETWORK_NAME" ]]; then
  echo "Unable to resolve docker network for $NETWORK_NAME" >&2
  exit 1
fi

NETWORK_ID="$(docker network inspect "$RESOLVED_NETWORK_NAME" --format '{{.Id}}')"
BRIDGE_NAME="$(docker network inspect "$RESOLVED_NETWORK_NAME" --format '{{index .Options "com.docker.network.bridge.name"}}' 2>/dev/null || true)"
if [[ -z "$BRIDGE_NAME" ]]; then
  BRIDGE_NAME="br-${NETWORK_ID:0:12}"
fi

mapfile -t SUBNETS < <(docker network inspect "$RESOLVED_NETWORK_NAME" --format '{{range .IPAM.Config}}{{println .Subnet}}{{end}}' | sed '/^$/d')
if [[ "${#SUBNETS[@]}" -eq 0 ]]; then
  echo "Unable to determine subnets for docker network $RESOLVED_NETWORK_NAME" >&2
  exit 1
fi

PORTS=(
  "${GATEWAY_PORT:-8080}"
  "${QUESTION_CORE_PORT:-8089}"
  "${OCR_PORT:-8090}"
  "${PERSIST_PORT:-8091}"
  "${EXPORT_SIDECAR_PORT:-8092}"
  "${EXAM_PORT:-8093}"
  "${EXAM_PARSE_PORT:-8094}"
  "${QUESTION_BASKET_PORT:-8097}"
)

PREROUTING_CHAIN="QFORGE_PUBLIC_HAIRPIN"
POSTROUTING_CHAIN="QFORGE_PUBLIC_HAIRPIN_SNAT"

delete_matching_rules nat PREROUTING "-d ${PUBLIC_IP}/32 .* -j DNAT --to-destination 172\\."
delete_matching_rules nat PREROUTING "-j ${PREROUTING_CHAIN}$"
delete_matching_rules nat POSTROUTING "-j ${POSTROUTING_CHAIN}$"

ensure_chain "$PREROUTING_CHAIN"
ensure_chain "$POSTROUTING_CHAIN"

for port in "${PORTS[@]}"; do
  iptables -t nat -A "$PREROUTING_CHAIN" -p tcp --dport "$port" -j DNAT --to-destination "$LOCAL_IP:$port"
  iptables -t nat -A "$POSTROUTING_CHAIN" -p tcp --dport "$port" -j MASQUERADE
done

for subnet in "${SUBNETS[@]}"; do
  iptables -t nat -A PREROUTING -s "$subnet" -d "$PUBLIC_IP"/32 -p tcp -j "$PREROUTING_CHAIN"
  iptables -t nat -A POSTROUTING -s "$subnet" -d "$LOCAL_IP"/32 -p tcp -j "$POSTROUTING_CHAIN"
done

echo "Resolved network: $RESOLVED_NETWORK_NAME ($BRIDGE_NAME)"
echo "Public IP: $PUBLIC_IP"
echo "Local IP: $LOCAL_IP"
printf 'Managed subnets:\n'
printf '  %s\n' "${SUBNETS[@]}"
iptables -t nat -S | grep 'QFORGE_PUBLIC_HAIRPIN' || true
