#!/usr/bin/env bash
echo "Stopping LedgerFlow processes started by this repo..."
for pidfile in /tmp/ledger-service.pid /tmp/fraud-service.pid /tmp/notification-service.pid /tmp/ingestion-service.pid /tmp/ledgerflow-dashboard.pid; do
  if [ -f "$pidfile" ]; then
    PID=$(cat "$pidfile")
    # Kill the launcher and its children. Do not kill every listener on the port:
    # another local app may already be bound there.
    pkill -P "$PID" 2>/dev/null || true
    kill "$PID" 2>/dev/null || true
    rm -f "$pidfile"
  fi
done
echo "LedgerFlow services stopped."
