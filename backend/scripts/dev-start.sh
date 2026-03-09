#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

LOG_DIR="$ROOT_DIR/.dev-logs"
mkdir -p "$LOG_DIR"

pkill -f "AuthServiceApplication" >/dev/null 2>&1 || true
pkill -f "QForgeGatewayApplication" >/dev/null 2>&1 || true
pkill -f "QuestionServiceApplication" >/dev/null 2>&1 || true
pkill -f "ExamServiceApplication" >/dev/null 2>&1 || true
pkill -f "ExamParseServiceApplication" >/dev/null 2>&1 || true
pkill -f "OcrServiceApplication" >/dev/null 2>&1 || true
pkill -f "spring-boot:run.*auth-service" >/dev/null 2>&1 || true
pkill -f "spring-boot:run.*gateway-service" >/dev/null 2>&1 || true
pkill -f "spring-boot:run.*question-service" >/dev/null 2>&1 || true
pkill -f "spring-boot:run.*exam-service" >/dev/null 2>&1 || true
pkill -f "spring-boot:run.*exam-parse-service" >/dev/null 2>&1 || true
pkill -f "spring-boot:run.*ocr-service" >/dev/null 2>&1 || true

echo "[dev] installing shared modules"
mvn -DskipTests -pl libs/common-contract,libs/internal-api-contract -am install

echo "[dev] starting auth-service on :8088"
mvn -DskipTests -pl services/auth-service spring-boot:run >"$LOG_DIR/auth-service.log" 2>&1 &
AUTH_PID=$!
echo "$AUTH_PID" >"$LOG_DIR/auth-service.pid"

sleep 5

echo "[dev] starting ocr-service on :8090"
mvn -DskipTests -pl services/ocr-service spring-boot:run >"$LOG_DIR/ocr-service.log" 2>&1 &
OCR_PID=$!
echo "$OCR_PID" >"$LOG_DIR/ocr-service.pid"

sleep 5

echo "[dev] starting question-core-service on :8089"
mvn -DskipTests -pl services/question-service spring-boot:run >"$LOG_DIR/question-service.log" 2>&1 &
QUESTION_PID=$!
echo "$QUESTION_PID" >"$LOG_DIR/question-service.pid"

sleep 3

echo "[dev] starting exam-service on :8093"
mvn -DskipTests -pl services/exam-service spring-boot:run >"$LOG_DIR/exam-service.log" 2>&1 &
EXAM_PID=$!
echo "$EXAM_PID" >"$LOG_DIR/exam-service.pid"

sleep 3

echo "[dev] starting exam-parse-service on :8094"
mvn -DskipTests -pl services/exam-parse-service spring-boot:run >"$LOG_DIR/exam-parse-service.log" 2>&1 &
EXAMPARSE_PID=$!
echo "$EXAMPARSE_PID" >"$LOG_DIR/exam-parse-service.pid"

sleep 5

echo "[dev] starting gateway-service on :8080"
mvn -DskipTests -pl services/gateway-service spring-boot:run >"$LOG_DIR/gateway-service.log" 2>&1 &
GATEWAY_PID=$!
echo "$GATEWAY_PID" >"$LOG_DIR/gateway-service.pid"

cleanup() {
  kill "$AUTH_PID" "$OCR_PID" "$QUESTION_PID" "$EXAM_PID" "$EXAMPARSE_PID" "$GATEWAY_PID" >/dev/null 2>&1 || true
}
trap cleanup INT TERM

echo "[dev] auth log: $LOG_DIR/auth-service.log"
echo "[dev] ocr log: $LOG_DIR/ocr-service.log"
echo "[dev] question-core log: $LOG_DIR/question-service.log"
echo "[dev] exam log: $LOG_DIR/exam-service.log"
echo "[dev] exam-parse log: $LOG_DIR/exam-parse-service.log"
echo "[dev] gateway log: $LOG_DIR/gateway-service.log"
wait -n "$AUTH_PID" "$OCR_PID" "$QUESTION_PID" "$EXAM_PID" "$EXAMPARSE_PID" "$GATEWAY_PID"
exit 1
