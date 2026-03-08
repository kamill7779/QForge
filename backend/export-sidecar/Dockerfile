FROM python:3.11-slim

LABEL maintainer="qforge-team"
LABEL service="export-sidecar"

WORKDIR /app

# 系统依赖 (可选: libxml2 用于 lxml 加速)
RUN apt-get update && apt-get install -y --no-install-recommends \
    libxml2 \
    && rm -rf /var/lib/apt/lists/*

# 先装依赖，利用 Docker 缓存
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# 复制源码
COPY . .

EXPOSE 8092

# 健康检查
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
    CMD python -c "import httpx; httpx.get('http://127.0.0.1:8092/api/export/health').raise_for_status()" || exit 1

CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8092", "--log-level", "info"]
