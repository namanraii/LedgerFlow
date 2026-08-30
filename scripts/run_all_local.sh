#!/usr/bin/env bash
set -e

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )/.." && pwd )"
echo "Starting LedgerFlow Services locally in background..."

# 1. Start Ledger Service
echo "Starting Ledger Service on port 8081..."
(cd "$DIR/ledger-service" && mvn spring-boot:run > /tmp/ledger-service.log 2>&1) &
LEDGER_PID=$!
echo $LEDGER_PID > /tmp/ledger-service.pid

# 2. Start Fraud Service
echo "Starting Fraud Service on port 8000..."
(cd "$DIR/fraud-service" && python3 main.py > /tmp/fraud-service.log 2>&1) &
FRAUD_PID=$!
echo $FRAUD_PID > /tmp/fraud-service.pid

# 3. Start Notification Service
echo "Starting Notification Service on port 8003..."
(cd "$DIR/notification-service" && python3 main.py > /tmp/notification-service.log 2>&1) &
NOTIF_PID=$!
echo $NOTIF_PID > /tmp/notification-service.pid

# 4. Start Ingestion Service
echo "Starting Ingestion Service on port 8082..."
(cd "$DIR/ingestion-service" && mvn spring-boot:run > /tmp/ingestion-service.log 2>&1) &
INGESTION_PID=$!
echo $INGESTION_PID > /tmp/ingestion-service.pid

echo "All services launched. Monitoring startup..."
sleep 8
echo "Ledger Service Health:"
curl -s http://localhost:8081/api/v1/health || echo "Waiting..."
echo ""
echo "Fraud Service Health:"
curl -s http://localhost:8000/api/v1/health || echo "Waiting..."
echo ""
echo "Ingestion Service Health:"
curl -s http://localhost:8082/api/v1/ingestion/health || echo "Waiting..."
echo ""
echo "Notification Service Health:"
curl -s http://localhost:8003/api/v1/health || echo "Waiting..."
echo ""
