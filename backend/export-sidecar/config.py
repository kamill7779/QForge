"""
export-sidecar 配置 — Nacos / 应用 / 数据源
"""
import os

# ── Nacos ──
NACOS_SERVER = os.getenv("NACOS_SERVER_ADDR", "localhost:8848")
SERVICE_NAME = "export-sidecar"
SERVICE_PORT = int(os.getenv("SERVICE_PORT", "8092"))
SERVICE_IP = os.getenv("SERVICE_IP", "")

# ── question-service 内部地址（Docker 网络内直连，不经 gateway）──
QUESTION_SERVICE_URL = os.getenv(
    "QUESTION_SERVICE_URL", "http://question-service:8089"
)

# ── 导出限制 ──
MAX_QUESTIONS_PER_EXPORT = int(os.getenv("MAX_QUESTIONS_PER_EXPORT", "200"))
MAX_DOCX_SIZE_MB = int(os.getenv("MAX_DOCX_SIZE_MB", "50"))
