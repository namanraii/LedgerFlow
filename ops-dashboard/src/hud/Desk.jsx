import { useState } from 'react';
import { clockTime, compact, money, percent, shortName } from '../format.js';

function Lamp({ on, label, warn }) {
  const tone = on ? 'on' : warn ? 'warn' : 'off';
  return (
    <span className={`lamp lamp-${tone}`}>
      <i />
      {label}
    </span>
  );
}

function Sparkline({ history }) {
  const points = [...(history || [])].reverse();
  if (points.length < 2) {
    return <p className="muted tiny">The audit trail appears after the first posting.</p>;
  }
  const values = points.map((row) => Number(row.balanceAfter));
  const min = Math.min(...values);
  const max = Math.max(...values);
  const width = 260;
  const height = 64;
  const path = values.map((value, index) => {
    const x = (index / (values.length - 1)) * width;
    const y = height - 6 - ((value - min) / (max - min || 1)) * (height - 12);
    return `${index === 0 ? 'M' : 'L'}${x.toFixed(1)},${y.toFixed(1)}`;
  }).join(' ');

  return (
    <svg className="spark" viewBox={`0 0 ${width} ${height}`} role="img" aria-label="Balance history">
      <path d={path} />
    </svg>
  );
}

function Composer({ accounts, health, busy, onPayment, onTransfer }) {
  const [mode, setMode] = useState('payment');
  const [accountId, setAccountId] = useState('');
  const [fromId, setFromId] = useState('');
  const [toId, setToId] = useState('');
  const [amount, setAmount] = useState('750.00');
  const [type, setType] = useState('DEBIT');
  const [memo, setMemo] = useState('Treasury sweep');
  const [error, setError] = useState('');

  const chosenAccount = accountId || accounts[0]?.accountId || '';
  const source = fromId || accounts[0]?.accountId || '';
  const destination = toId || accounts[1]?.accountId || accounts[0]?.accountId || '';

  async function submit(event) {
    event.preventDefault();
    setError('');
    const parsed = Number(amount);
    if (!Number.isFinite(parsed) || parsed <= 0) {
      setError('Enter an amount greater than zero.');
      return;
    }
    try {
      if (mode === 'payment') {
        if (!chosenAccount) {
          setError('Open an account before posting.');
          return;
        }
        await onPayment({ accountId: Number(chosenAccount), amount: parsed, transactionType: type });
      } else {
        if (!source || !destination || Number(source) === Number(destination)) {
          setError('A book transfer needs two different accounts.');
          return;
        }
        await onTransfer({
          fromAccountId: Number(source),
          toAccountId: Number(destination),
          amount: parsed,
          memo,
        });
      }
    } catch (err) {
      setError(err.message || 'The ledger rejected the posting.');
    }
  }

  return (
    <form className="composer" onSubmit={submit}>
      <div className="segmented" role="tablist" aria-label="Posting type">
        <button type="button" className={mode === 'payment' ? 'is-on' : ''} onClick={() => setMode('payment')}>Payment</button>
        <button type="button" className={mode === 'transfer' ? 'is-on' : ''} onClick={() => setMode('transfer')}>Book transfer</button>
      </div>
      {mode === 'payment' ? (
        <>
          <label>
            Account
            <select value={chosenAccount} onChange={(event) => setAccountId(event.target.value)}>
              {accounts.map((account) => (
                <option key={account.accountId} value={account.accountId}>
                  {shortName(account.accountHolder)} · {money(account.balance)}
                </option>
              ))}
            </select>
          </label>
          <div className="split">
            <label>
              Amount
              <input inputMode="decimal" value={amount} onChange={(event) => setAmount(event.target.value)} />
            </label>
            <label>
              Side
              <select value={type} onChange={(event) => setType(event.target.value)}>
                <option value="DEBIT">Debit</option>
                <option value="CREDIT">Credit</option>
              </select>
            </label>
          </div>
          <p className="hint">
            {health.ingestion
              ? 'Fraud scores this, then the ledger locks the row.'
              : 'Ingestion is offline, so this posts with fraud advisory skipped.'}
          </p>
        </>
      ) : (
        <>
          <div className="split">
            <label>
              From
              <select value={source} onChange={(event) => setFromId(event.target.value)}>
                {accounts.map((account) => (
                  <option key={account.accountId} value={account.accountId}>{shortName(account.accountHolder)}</option>
                ))}
              </select>
            </label>
            <label>
              To
              <select value={destination} onChange={(event) => setToId(event.target.value)}>
                {accounts.map((account) => (
                  <option key={account.accountId} value={account.accountId}>{shortName(account.accountHolder)}</option>
                ))}
              </select>
            </label>
          </div>
          <label>
            Amount
            <input inputMode="decimal" value={amount} onChange={(event) => setAmount(event.target.value)} />
          </label>
          <label>
            Memo
            <input value={memo} maxLength={160} onChange={(event) => setMemo(event.target.value)} />
          </label>
          <p className="hint">Both accounts lock in id order, so two opposing sweeps cannot deadlock. One idempotency key covers both legs.</p>
        </>
      )}
      {error && <p className="form-error">{error}</p>}
      <button className="submit" type="submit" disabled={busy || accounts.length === 0}>
        {busy ? 'Posting…' : mode === 'payment' ? 'Post payment' : 'Move funds'}
      </button>
    </form>
  );
}

export default function Desk({
  health,
  summary,
  metrics,
  accounts,
  transactions,
  flagged,
  alerts,
  selectedId,
  accountDetail,
  history,
  busy,
  notice,
  fault,
  scene,
  onSelect,
  onPayment,
  onTransfer,
  onReview,
}) {
  const selected = accountDetail && Number(accountDetail.accountId) === Number(selectedId) ? accountDetail : null;

  return (
    <>
      <header className="topbar">
        <div className="brand">
          <span className="seal" aria-hidden="true">L</span>
          <div>
            <p className="eyebrow">Clearing desk</p>
            <h1>LedgerFlow</h1>
          </div>
        </div>
        <p className="tagline">Row-locked balances. Advisory fraud. One book of record.</p>
        <div className="lamps" aria-label="Service health">
          <Lamp on={health.ledger} label="Ledger" />
          <Lamp on={health.ingestion} label="Ingestion" />
          <Lamp on={health.fraud} label={health.fraud ? 'Fraud' : 'Fraud degraded'} warn />
          <Lamp on={health.notification} label="Signals" />
        </div>
      </header>

      <aside className="rail rail-left">
        <section className="panel">
          <p className="eyebrow">Assets on the book</p>
          <p className="figure">{summary ? money(summary.assetsUnderManagement) : '—'}</p>
          <dl className="stats">
            <div><dt>Debit volume</dt><dd>{summary ? compact(summary.debitVolume) : '—'}</dd></div>
            <div><dt>Credit volume</dt><dd>{summary ? compact(summary.creditVolume) : '—'}</dd></div>
            <div><dt>Open flags</dt><dd>{summary ? summary.unreviewedFlags : '—'}</dd></div>
            <div><dt>Avg risk</dt><dd>{summary ? percent(summary.averageRisk) : '—'}</dd></div>
          </dl>
          <p className="hint">
            {metrics
              ? `Ingestion has accepted ${metrics.total_processed} payments · ${metrics.active_threads} workers busy · queue ${metrics.queue_depth}/${metrics.queue_capacity}.`
              : 'Ingestion metrics appear when that service is up.'}
          </p>
        </section>

        {!health.ledger && (
          <section className="panel warning-panel">
            <p className="eyebrow">Ledger offline</p>
            <p>Nothing on this desk can move money until the ledger answers on port 8081. Start the stack with <code>./scripts/run_all_local.sh</code>.</p>
          </section>
        )}
        {health.ledger && fault && (
          <section className="panel warning-panel">
            <p className="eyebrow">Ledger error</p>
            <p>{fault}</p>
          </section>
        )}

        <section className="panel post-panel">
          <div className="panel-head">
            <h2>Post</h2>
          </div>
          <Composer accounts={accounts} health={health} busy={busy} onPayment={onPayment} onTransfer={onTransfer} />
        </section>

        <section className="panel accounts book-panel">
          <div className="panel-head">
            <h2>Books</h2>
            <span>{accounts.length}</span>
          </div>
          <ul>
            {accounts.map((account) => (
              <li key={account.accountId}>
                <button
                  type="button"
                  className={Number(selectedId) === Number(account.accountId) ? 'is-on' : ''}
                  onClick={() => onSelect(account.accountId)}
                >
                  <span>
                    <strong>{shortName(account.accountHolder)}</strong>
                    <em>#{account.accountId}</em>
                  </span>
                  <b>{money(account.balance)}</b>
                </button>
              </li>
            ))}
            {accounts.length === 0 && <li className="muted">Waiting for the ledger.</li>}
          </ul>
        </section>
      </aside>

      <main className="stage">
        {scene}
        <p className="stage-hint">Drag to look around the floor. Click a bar to open its book. Height follows log balance. Crimson means an open flag.</p>
        {selected && (
          <article className="account-card">
            <header>
              <div>
                <p className="eyebrow">Account #{selected.accountId}</p>
                <h2>{selected.accountHolder}</h2>
              </div>
              <button type="button" className="ghost" onClick={() => onSelect(null)}>Close</button>
            </header>
            <p className="figure small">{money(selected.balance)}</p>
            <Sparkline history={history} />
            <ol className="mutations">
              {(history || []).slice(0, 4).map((row) => {
                const delta = Number(row.balanceAfter) - Number(row.balanceBefore);
                return (
                  <li key={row.id}>
                    <span>#{row.transactionId}</span>
                    <b className={delta >= 0 ? 'up' : 'down'}>{delta >= 0 ? '+' : '−'}{money(Math.abs(delta))}</b>
                    <em>{clockTime(row.createdAt)}</em>
                  </li>
                );
              })}
            </ol>
          </article>
        )}
      </main>

      <aside className="rail rail-right">
        <section className="panel queue">
          <div className="panel-head">
            <h2>Unreviewed</h2>
            <span>{flagged.length}</span>
          </div>
          {flagged.length === 0 ? (
            <p className="empty">The queue is clear. Flagged payments land here, ranked inside each account by risk.</p>
          ) : (
            <ul className="cases">
              {flagged.map((flag) => {
                const score = Number(flag.riskScore) || 0;
                return (
                  <li key={flag.flagId} className={score >= 0.85 ? 'is-hot' : ''}>
                    <header>
                      <button type="button" onClick={() => onSelect(flag.accountId)}>{shortName(flag.accountHolder)}</button>
                      <b>{money(flag.amount)}</b>
                    </header>
                    <p>{flag.reason}</p>
                    <div className="meter" aria-label={`Risk ${percent(score)}`}>
                      <span style={{ width: `${Math.round(score * 100)}%` }} />
                    </div>
                    <footer>
                      <em>Rank {flag.riskRank} · tx #{flag.transactionId}</em>
                      <div>
                        <button type="button" onClick={() => onReview(flag.flagId, 'APPROVED')}>Approve</button>
                        <button type="button" className="danger" onClick={() => onReview(flag.flagId, 'BLOCKED')}>Block</button>
                      </div>
                    </footer>
                  </li>
                );
              })}
            </ul>
          )}
        </section>

        <section className="panel signals">
          <div className="panel-head">
            <h2>Live signals</h2>
            <span>{health.notification ? 'sse' : 'off'}</span>
          </div>
          {alerts.length === 0 ? (
            <p className="empty">Advisory alerts from the fraud service stream here. They do not move money. The queue above does.</p>
          ) : (
            <ul>
              {alerts.map((alert, index) => (
                <li key={`${alert.received_at || index}-${alert.account_id}`}>
                  <strong>Acct #{alert.account_id}</strong>
                  <span>{money(alert.amount)} · {percent(alert.risk_score)}</span>
                  <em>{alert.reason}</em>
                </li>
              ))}
            </ul>
          )}
        </section>
      </aside>

      <footer className="tape" aria-label="Recent postings">
        {transactions.length === 0 && <p className="empty tape-empty">Postings will run along this rail.</p>}
        {transactions.map((tx) => (
          <button key={tx.transactionId} type="button" className={`ticket ticket-${(tx.status || '').toLowerCase()}`} onClick={() => onSelect(tx.accountId)}>
            <span>{tx.transactionType === 'CREDIT' ? 'Credit' : 'Debit'}</span>
            <strong>{money(tx.amount)}</strong>
            <em>{tx.status}</em>
            <small>{tx.memo || `Account #${tx.accountId}`} · {clockTime(tx.createdAt)}</small>
          </button>
        ))}
      </footer>

      {notice && <div className={`toast toast-${notice.tone}`} role="status">{notice.text}</div>}
    </>
  );
}
