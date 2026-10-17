#!/usr/bin/env bash
set -euo pipefail

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )/.." && pwd )"

if [ -d "/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home" ]; then
  export JAVA_HOME="/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home"
  export PATH="$JAVA_HOME/bin:$PATH"
fi

VENV="$DIR/venv"
if [ ! -x "$VENV/bin/python" ]; then
  echo "Creating a local virtualenv at venv/"
  python3 -m venv "$VENV"
fi
PYTHON="$VENV/bin/python"
echo "Starting LedgerFlow with $(java -version 2>&1 | head -1) and $PYTHON"

if ! "$PYTHON" -c "import fastapi, uvicorn, pika" >/dev/null 2>&1; then
  echo "Installing Python dependencies into venv/"
  "$PYTHON" -m pip install -q -r "$DIR/fraud-service/requirements.txt" -r "$DIR/notification-service/requirements.txt"
fi

echo "Starting Ledger Service on port 8081..."
(cd "$DIR/ledger-service" && mvn -q spring-boot:run > /tmp/ledger-service.log 2>&1) &
echo $! > /tmp/ledger-service.pid

echo "Starting Fraud Service on port 8010..."
(cd "$DIR/fraud-service" && PORT=8010 "$PYTHON" main.py > /tmp/fraud-service.log 2>&1) &
echo $! > /tmp/fraud-service.pid

echo "Starting Notification Service on port 8003..."
(cd "$DIR/notification-service" && "$PYTHON" main.py > /tmp/notification-service.log 2>&1) &
echo $! > /tmp/notification-service.pid

echo "Starting Ingestion Service on port 8082..."
(cd "$DIR/ingestion-service" && FRAUD_SERVICE_URL=http://127.0.0.1:8010 LEDGER_SERVICE_URL=http://127.0.0.1:8081 mvn -q spring-boot:run > /tmp/ingestion-service.log 2>&1) &
echo $! > /tmp/ingestion-service.pid

wait_for() {
  local name="$1"
  local url="$2"
  local log="$3"
  local i
  for i in $(seq 1 45); do
    if curl -sf "$url" >/dev/null 2>&1; then
      echo "$name is up"
      return 0
    fi
    sleep 2
  done
  echo "$name did not become healthy. Last log lines:"
  tail -n 40 "$log" || true
  return 1
}

wait_for "Ledger" "http://127.0.0.1:8081/api/v1/health" /tmp/ledger-service.log
wait_for "Fraud" "http://127.0.0.1:8010/api/v1/health" /tmp/fraud-service.log
wait_for "Ingestion" "http://127.0.0.1:8082/api/v1/ingestion/health" /tmp/ingestion-service.log
wait_for "Notification" "http://127.0.0.1:8003/api/v1/health" /tmp/notification-service.log

echo ""
echo "Services are up. Start the desk with:"
echo "  cd ops-dashboard && npm install && npm run dev"
echo "Then open http://127.0.0.1:5188"
