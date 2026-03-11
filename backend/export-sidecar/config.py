"""
export-sidecar 配置 — Nacos / 应用 / 数据源
"""
import os

# ── Nacos ──
NACOS_SERVER = os.getenv("NACOS_SERVER_ADDR", "localhost:8848")
NACOS_USERNAME = os.getenv("NACOS_USERNAME", "")
NACOS_PASSWORD = os.getenv("NACOS_PASSWORD", "")
NACOS_REGISTER_RETRIES = int(os.getenv("NACOS_REGISTER_RETRIES", "20"))
NACOS_REGISTER_RETRY_INTERVAL_SECONDS = float(
	os.getenv("NACOS_REGISTER_RETRY_INTERVAL_SECONDS", "3")
)
NACOS_HEARTBEAT_INTERVAL_SECONDS = float(
	os.getenv("NACOS_HEARTBEAT_INTERVAL_SECONDS", "5")
)
SERVICE_NAME = "export-sidecar"
SERVICE_PORT = int(os.getenv("SERVICE_PORT", "8092"))
SERVICE_IP = os.getenv("SERVICE_IP", "")

# ── 导出限制 ──
MAX_QUESTIONS_PER_EXPORT = int(os.getenv("MAX_QUESTIONS_PER_EXPORT", "200"))
MAX_DOCX_SIZE_MB = int(os.getenv("MAX_DOCX_SIZE_MB", "50"))
