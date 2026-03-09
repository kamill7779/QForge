#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

docker compose up -d --build gateway-service gaokao-corpus-service gaokao-analysis-service gaokao-web
docker compose ps gateway-service gaokao-corpus-service gaokao-analysis-service gaokao-web