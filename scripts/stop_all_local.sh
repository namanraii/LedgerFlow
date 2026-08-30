#!/usr/bin/env bash
echo "Stopping all LedgerFlow background processes..."
for pidfile in /tmp/ledger-service.pid /tmp/fraud-service.pid /tmp/notification-service.pid /tmp/ingestion-service.pid; do
  if [ -f "$pidfile" ]; then
    PID=$(cat "$pidfile")
    kill $PID 2>/dev/null || true
    rm "$pidfile"
  fi
done
# Kill any lingering ports
lsof -ti:8081 | xargs kill -9 2>/dev/null || true
lsof -ti:8082 | xargs kill -9 2>/dev/null || true
lsof -ti:8000 | xargs kill -9 2>/dev/null || true
lsof -ti:8003 | xargs kill -9 2>/dev/null || true
echo "All LedgerFlow services stopped."
