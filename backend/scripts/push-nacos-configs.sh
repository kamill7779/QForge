#!/usr/bin/env bash
# ============================================================
# push-nacos-configs.sh
# 将微服务拆分后的配置推送到 Nacos 配置中心
# 用法: bash scripts/push-nacos-configs.sh [NACOS_ADDR]
# ============================================================
set -euo pipefail

NACOS="${1:-http://localhost:8848}"

echo "============================================="
echo "  QForge — Nacos 配置推送"
echo "  Nacos: $NACOS"
echo "============================================="

# 等待 Nacos 就绪
echo "[1/5] 等待 Nacos 就绪..."
for i in $(seq 1 30); do
  if curl -sf "$NACOS/nacos/v1/cs/configs?dataId=__probe__&group=DEFAULT_GROUP" >/dev/null 2>&1; then
    echo "  Nacos 已就绪"
    break
  fi
  if [ "$i" -eq 30 ]; then
    echo "  ERROR: Nacos 30s 内未就绪，请检查容器状态"
    exit 1
  fi
  sleep 1
done

# ---------- 删除旧的 question-service.yml (已拆分为 question-core-service) ----------
echo "[2/5] 清理旧配置 question-service.yml ..."
curl -sf -X DELETE "$NACOS/nacos/v1/cs/configs?dataId=question-service.yml&group=DEFAULT_GROUP" >/dev/null 2>&1 || true
echo "  done (如不存在则跳过)"

# ---------- question-core-service.yml ----------
echo "[3/5] 推送 question-core-service.yml ..."
curl -sf -X POST "$NACOS/nacos/v1/cs/configs" \
  --data-urlencode "dataId=question-core-service.yml" \
  --data-urlencode "group=DEFAULT_GROUP" \
  --data-urlencode "type=yaml" \
  --data-urlencode "content=# question-core-service 热配置 (可运行时覆盖, 无需重启)
# 覆盖 application.yml 中的 qforge.business.* 即可热生效
qforge:
  business:
    max-inline-images: 10
    max-image-binary-bytes: 524288
    max-reasoning-length: 1024
    max-error-message-length: 2048
    task-state-ttl-minutes: 30
    answer-ocr-guard-ttl-minutes: 10
    answer-ocr-asset-ttl-hours: 6
    asset-cache-ttl-seconds: 30
    ws-allowed-origins: \"*\"
"
echo "  → question-core-service.yml ✓"

# ---------- exam-service.yml ----------
echo "[4/5] 推送 exam-service.yml ..."
curl -sf -X POST "$NACOS/nacos/v1/cs/configs" \
  --data-urlencode "dataId=exam-service.yml" \
  --data-urlencode "group=DEFAULT_GROUP" \
  --data-urlencode "type=yaml" \
  --data-urlencode "content=# exam-service 热配置 (可运行时覆盖, 无需重启)
# 当前无需额外覆盖项, 保留占位以便后续添加
logging:
  level:
    io.github.kamill7779.qforge.exam: INFO
"
echo "  → exam-service.yml ✓"

# ---------- exam-parse-service.yml ----------
echo "[5/5] 推送 exam-parse-service.yml ..."
curl -sf -X POST "$NACOS/nacos/v1/cs/configs" \
  --data-urlencode "dataId=exam-parse-service.yml" \
  --data-urlencode "group=DEFAULT_GROUP" \
  --data-urlencode "type=yaml" \
  --data-urlencode "content=# exam-parse-service 热配置 (可运行时覆盖, 无需重启)
qforge:
  business:
    max-exam-upload-files: 5
    allowed-exam-extensions: pdf,jpg,jpeg,png

logging:
  level:
    io.github.kamill7779.qforge.examparse: INFO
"
echo "  → exam-parse-service.yml ✓"

echo ""
echo "============================================="
echo "  全部配置推送完成！"
echo "  可在 Nacos 控制台查看: $NACOS/nacos/"
echo "============================================="
