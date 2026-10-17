import { lazy, Suspense, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { FRAUD, INGESTION, LEDGER, NOTIFY, ping, requestJson } from './api.js';
import Desk from './hud/Desk.jsx';

const LedgerScene = lazy(() => import('./scene/LedgerScene.jsx'));

const reduceMotion = typeof window !== 'undefined'
  && window.matchMedia('(prefers-reduced-motion: reduce)').matches;

export default function App() {
  const [health, setHealth] = useState({ ledger: false, ingestion: false, fraud: false, notification: false });
  const [accounts, setAccounts] = useState([]);
  const [transactions, setTransactions] = useState([]);
  const [flagged, setFlagged] = useState([]);
  const [summary, setSummary] = useState(null);
  const [metrics, setMetrics] = useState(null);
  const [alerts, setAlerts] = useState([]);
  const [selectedId, setSelectedId] = useState(null);
  const [accountDetail, setAccountDetail] = useState(null);
  const [history, setHistory] = useState([]);
  const [flights, setFlights] = useState([]);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState(null);
  const [fault, setFault] = useState('');
  const seen = useRef(new Set());
  const primed = useRef(false);
  const selectedRef = useRef(null);
  selectedRef.current = selectedId;

  const showNotice = useCallback((text, tone = 'ok') => {
    setNotice({ text, tone });
    window.clearTimeout(showNotice.timer);
    showNotice.timer = window.setTimeout(() => setNotice(null), 4200);
  }, []);

  const refresh = useCallback(async () => {
    const [ledger, ingestion, fraud, notification] = await Promise.all([
      ping(`${LEDGER}/health`),
      ping(`${INGESTION}/ingestion/health`),
      ping(`${FRAUD}/health`),
      ping(`${NOTIFY}/health`),
    ]);
    setHealth({ ledger, ingestion, fraud, notification });

    if (ledger) {
      try {
        const tasks = [
          requestJson(`${LEDGER}/accounts`),
          requestJson(`${LEDGER}/transactions?size=18`),
          requestJson(`${LEDGER}/reports/flagged`),
          requestJson(`${LEDGER}/dashboard/summary`),
        ];
        if (selectedRef.current) {
          tasks.push(requestJson(`${LEDGER}/accounts/${selectedRef.current}`));
          tasks.push(requestJson(`${LEDGER}/accounts/${selectedRef.current}/history`));
        }
        const results = await Promise.all(tasks);
        setAccounts(results[0] || []);
        setTransactions(results[1]?.content || []);
        setFlagged(results[2] || []);
        setSummary(results[3]);
        if (selectedRef.current) {
          setAccountDetail(results[4]);
          setHistory(results[5] || []);
        }
        setFault('');
      } catch (error) {
        setFault(error.message);
      }
    }

    if (ingestion) {
      try {
        setMetrics(await requestJson(`${INGESTION}/ingestion/metrics`));
      } catch {
        setMetrics(null);
      }
    } else {
      setMetrics(null);
    }
  }, [showNotice]);

  useEffect(() => {
    refresh();
    const id = window.setInterval(() => {
      if (!document.hidden) refresh();
    }, 2500);
    return () => window.clearInterval(id);
  }, [refresh]);

  useEffect(() => {
    if (!selectedId) {
      setAccountDetail(null);
      setHistory([]);
      return undefined;
    }
    let cancelled = false;
    (async () => {
      try {
        const [detail, rows] = await Promise.all([
          requestJson(`${LEDGER}/accounts/${selectedId}`),
          requestJson(`${LEDGER}/accounts/${selectedId}/history`),
        ]);
        if (cancelled) return;
        setAccountDetail(detail);
        setHistory(rows || []);
      } catch (error) {
        if (!cancelled) setFault(error.message);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [selectedId]);

  useEffect(() => {
    if (!transactions.length) return;
    if (!primed.current) {
      transactions.forEach((tx) => seen.current.add(tx.transactionId));
      primed.current = true;
      return;
    }
    const fresh = [];
    transactions.forEach((tx) => {
      if (seen.current.has(tx.transactionId)) return;
      seen.current.add(tx.transactionId);
      if (tx.counterpartyAccountId && tx.transactionType === 'CREDIT') return;
      fresh.push(tx);
    });
    if (fresh.length && !reduceMotion) {
      setFlights((current) => [...current, ...fresh].slice(-10));
    }
  }, [transactions]);

  useEffect(() => {
    if (!health.notification) return undefined;
    const source = new EventSource(`${NOTIFY}/alerts/stream`);
    source.onmessage = (event) => {
      try {
        const alert = JSON.parse(event.data);
        setAlerts((current) => [alert, ...current].slice(0, 6));
      } catch {
        /* ignore malformed frames */
      }
    };
    return () => source.close();
  }, [health.notification]);

  const riskByAccount = useMemo(() => {
    const scores = {};
    flagged.forEach((flag) => {
      const id = flag.accountId;
      scores[id] = Math.max(scores[id] || 0, Number(flag.riskScore) || 0);
    });
    return scores;
  }, [flagged]);

  async function postPayment(payload) {
    setBusy(true);
    try {
      const idempotencyKey = crypto.randomUUID();
      let result;
      if (health.ingestion) {
        result = await requestJson(`${INGESTION}/ingestion/submit`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ ...payload, idempotencyKey, safe: true }),
        });
      } else {
        result = await requestJson(`${LEDGER}/transactions`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ ...payload, idempotencyKey, safe: true, fraudCheckStatus: 'UNAVAILABLE' }),
        });
      }
      const tone = result.status === 'COMPLETED' ? 'ok' : 'warn';
      const detail = result.errorMessage ? ` ${result.errorMessage}` : '';
      showNotice(`${result.status || 'Posted'} · #${result.transactionId || '—'}${detail}`, tone);
      await refresh();
    } finally {
      setBusy(false);
    }
  }

  async function postTransfer(payload) {
    setBusy(true);
    try {
      const result = await requestJson(`${LEDGER}/transfers`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ ...payload, idempotencyKey: crypto.randomUUID() }),
      });
      showNotice(`Transfer posted · debit #${result.debitTransactionId} to #${result.toAccountId}`);
      await refresh();
    } finally {
      setBusy(false);
    }
  }

  async function review(flagId, reviewerAction) {
    try {
      await requestJson(`${LEDGER}/fraud-flags/${flagId}/review`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ reviewerAction }),
      });
      showNotice(reviewerAction === 'BLOCKED'
        ? 'Blocked. A reversing entry restored the account.'
        : 'Approved. The posting stands.');
      await refresh();
    } catch (error) {
      showNotice(error.message, 'warn');
    }
  }

  return (
    <div className="app">
      <Desk
        scene={(
          <Suspense fallback={<div className="scene-fallback">Opening the floor…</div>}>
            <LedgerScene
              accounts={accounts}
              riskByAccount={riskByAccount}
              flights={flights}
              selectedId={selectedId}
              reduceMotion={reduceMotion}
              onSelect={setSelectedId}
              onFlightDone={(id) => setFlights((current) => current.filter((flight) => flight.transactionId !== id))}
            />
          </Suspense>
        )}
        health={health}
        summary={summary}
        metrics={metrics}
        accounts={accounts}
        transactions={transactions}
        flagged={flagged}
        alerts={alerts}
        selectedId={selectedId}
        accountDetail={accountDetail}
        history={history}
        busy={busy}
        notice={notice}
        fault={fault}
        onSelect={setSelectedId}
        onPayment={postPayment}
        onTransfer={postTransfer}
        onReview={review}
      />
    </div>
  );
}
