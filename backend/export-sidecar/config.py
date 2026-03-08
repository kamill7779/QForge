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

# ── 独立测试模式: 直连 DB（仅在 question-service 内部 API 未就绪时使用）──
STANDALONE_MODE = os.getenv("STANDALONE_MODE", "false").lower() in ("true", "1", "yes")

DB_CONFIG = dict(
    host=os.getenv("MYSQL_HOST", "localhost"),
    port=int(os.getenv("MYSQL_PORT", "3306")),
    user=os.getenv("MYSQL_USER", "qforge"),
    password=os.getenv("MYSQL_PASSWORD", "qforge"),
    database=os.getenv("MYSQL_DB", "qforge"),
)

# ── 导出限制 ──
MAX_QUESTIONS_PER_EXPORT = int(os.getenv("MAX_QUESTIONS_PER_EXPORT", "200"))
MAX_DOCX_SIZE_MB = int(os.getenv("MAX_DOCX_SIZE_MB", "50"))
