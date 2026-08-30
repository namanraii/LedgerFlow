import React, { useState, useEffect } from 'react';
import { 
  ShieldAlert, 
  Activity, 
  CheckCircle2, 
  XCircle, 
  Clock, 
  CreditCard, 
  Database, 
  Server, 
  RefreshCw, 
  TrendingUp, 
  AlertTriangle, 
  Search, 
  ArrowUpRight, 
  ArrowDownLeft, 
  Info,
  Lock,
  Layers
} from 'lucide-react';

const LEDGER_API = 'http://localhost:8081/api/v1';
const INGESTION_API = 'http://localhost:8082/api/v1';
const FRAUD_API = 'http://localhost:8000/api/v1';
const NOTIFICATION_API = 'http://localhost:8003/api/v1';

export default function App() {
  const [activeTab, setActiveTab] = useState('triage');
  const [transactions, setTransactions] = useState([]);
  const [flaggedReport, setFlaggedReport] = useState([]);
  const [servicesStatus, setServicesStatus] = useState({
    ledger: false,
    ingestion: false,
    fraud: false,
    notification: false
  });
  const [selectedAccountId, setSelectedAccountId] = useState('1');
  const [accountData, setAccountData] = useState(null);
  const [accountHistory, setAccountHistory] = useState([]);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [quickAmount, setQuickAmount] = useState('150.00');
  const [quickType, setQuickType] = useState('DEBIT');
  const [quickAccId, setQuickAccId] = useState('1');
  const [notificationMsg, setNotificationMsg] = useState(null);

  // Poll system state
  const fetchData = async () => {
    // 1. Service Health Checks
    try {
      const [lRes, iRes, fRes, nRes] = await Promise.allSettled([
        fetch(`${LEDGER_API}/health`),
        fetch(`${INGESTION_API}/ingestion/health`),
        fetch(`${FRAUD_API}/health`),
        fetch(`${NOTIFICATION_API}/health`)
      ]);
      setServicesStatus({
        ledger: lRes.status === 'fulfilled' && lRes.value.ok,
        ingestion: iRes.status === 'fulfilled' && iRes.value.ok,
        fraud: fRes.status === 'fulfilled' && fRes.value.ok,
        notification: nRes.status === 'fulfilled' && nRes.value.ok
      });
    } catch (e) {
      console.warn('Health check error:', e);
    }

    // 2. Fetch Recent Transactions
    try {
      const res = await fetch(`${LEDGER_API}/transactions?size=25`);
      if (res.ok) {
        const data = await res.json();
        setTransactions(data.content || []);
      }
    } catch (e) {}

    // 3. Fetch Flagged Report (SQL Window Function Query)
    try {
      const res = await fetch(`${LEDGER_API}/reports/flagged`);
      if (res.ok) {
        const data = await res.json();
        setFlaggedReport(data || []);
      }
    } catch (e) {}
  };

  const fetchAccountDetails = async (accId) => {
    if (!accId) return;
    try {
      const [accRes, histRes] = await Promise.all([
        fetch(`${LEDGER_API}/accounts/${accId}`),
        fetch(`${LEDGER_API}/accounts/${accId}/history`)
      ]);
      if (accRes.ok) setAccountData(await accRes.json());
      if (histRes.ok) setAccountHistory(await histRes.json());
    } catch (e) {
      console.error(e);
    }
  };

  useEffect(() => {
    fetchData();
    const interval = setInterval(fetchData, 3000);
    return () => clearInterval(interval);
  }, []);

  useEffect(() => {
    fetchAccountDetails(selectedAccountId);
  }, [selectedAccountId]);

  const handleReview = async (flagId, action) => {
    try {
      const res = await fetch(`${LEDGER_API}/fraud-flags/${flagId}/review`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ reviewerAction: action })
      });
      if (res.ok) {
        setNotificationMsg(`Transaction successfully ${action.toLowerCase()}!`);
        setTimeout(() => setNotificationMsg(null), 3000);
        fetchData();
      }
    } catch (e) {
      console.error(e);
    }
  };

  const handleQuickSubmit = async (e) => {
    e.preventDefault();
    setIsSubmitting(true);
    try {
      const res = await fetch(`${INGESTION_API}/ingestion/submit`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          accountId: parseInt(quickAccId),
          amount: parseFloat(quickAmount),
          transactionType: quickType,
          safe: true
        })
      });
      const data = await res.json();
      setNotificationMsg(`Transaction processed: ${data.status} (ID: ${data.transactionId || 'N/A'})`);
      setTimeout(() => setNotificationMsg(null), 4000);
      fetchData();
      if (selectedAccountId === quickAccId) fetchAccountDetails(selectedAccountId);
    } catch (e) {
      console.error(e);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col font-sans">
      {/* Top Header */}
      <header className="border-b border-slate-800 bg-slate-900/80 backdrop-blur sticky top-0 z-50 px-6 py-4 flex flex-wrap items-center justify-between gap-4">
        <div className="flex items-center space-x-3">
          <div className="p-2 bg-sky-500/10 border border-sky-500/30 rounded-lg text-sky-400">
            <Lock className="w-6 h-6" />
          </div>
          <div>
            <div className="flex items-center space-x-2">
              <h1 className="text-xl font-bold text-white tracking-tight">LedgerFlow</h1>
              <span className="text-xs px-2 py-0.5 rounded bg-sky-950 border border-sky-800 text-sky-300 font-mono">
                v2.0-core
              </span>
            </div>
            <p className="text-xs text-slate-400">Concurrent Transaction Processing &amp; Risk Triage Platform</p>
          </div>
        </div>

        {/* Microservice Health Indicators */}
        <div className="flex items-center space-x-2 text-xs bg-slate-950/70 border border-slate-800 p-1.5 rounded-lg">
          <div className="flex items-center space-x-1.5 px-2 py-1 rounded bg-slate-900">
            <span className={`w-2 h-2 rounded-full ${servicesStatus.ledger ? 'bg-emerald-400 animate-pulse' : 'bg-red-500'}`}></span>
            <span className="text-slate-300">Ledger (8081)</span>
          </div>
          <div className="flex items-center space-x-1.5 px-2 py-1 rounded bg-slate-900">
            <span className={`w-2 h-2 rounded-full ${servicesStatus.ingestion ? 'bg-emerald-400' : 'bg-red-500'}`}></span>
            <span className="text-slate-300">Ingestion (8082)</span>
          </div>
          <div className="flex items-center space-x-1.5 px-2 py-1 rounded bg-slate-900">
            <span className={`w-2 h-2 rounded-full ${servicesStatus.fraud ? 'bg-emerald-400' : 'bg-amber-400'}`}></span>
            <span className="text-slate-300">{servicesStatus.fraud ? 'Fraud (8000)' : 'Fraud (Degraded)'}</span>
          </div>
          <div className="flex items-center space-x-1.5 px-2 py-1 rounded bg-slate-900">
            <span className={`w-2 h-2 rounded-full ${servicesStatus.notification ? 'bg-emerald-400' : 'bg-slate-500'}`}></span>
            <span className="text-slate-300">Notif (8003)</span>
          </div>
        </div>
      </header>

      {/* Degradation Warning Banner */}
      {!servicesStatus.fraud && (
        <div className="bg-amber-950/60 border-b border-amber-800/60 px-6 py-2 flex items-center justify-between text-xs text-amber-200">
          <div className="flex items-center space-x-2">
            <AlertTriangle className="w-4 h-4 text-amber-400" />
            <span><strong>Degraded Mode Active:</strong> Fraud Detection Service offline. Ledger is processing transactions in fail-open mode (risk_score=NULL, status=UNAVAILABLE).</span>
          </div>
          <span className="font-mono bg-amber-900/50 px-2 py-0.5 rounded">Fail-Open Advisory</span>
        </div>
      )}

      {/* User Notification Toast */}
      {notificationMsg && (
        <div className="fixed bottom-6 right-6 z-50 bg-emerald-950 border border-emerald-700 text-emerald-200 px-4 py-3 rounded-lg shadow-xl flex items-center space-x-2 animate-bounce">
          <CheckCircle2 className="w-5 h-5 text-emerald-400" />
          <span>{notificationMsg}</span>
        </div>
      )}

      {/* Main Content Layout */}
      <div className="flex-1 max-w-7xl w-full mx-auto p-6 space-y-6">
        {/* Navigation Tabs & Quick Injector Bar */}
        <div className="flex flex-col md:flex-row items-start md:items-center justify-between gap-4 border-b border-slate-800 pb-4">
          <nav className="flex space-x-1 bg-slate-900 p-1 rounded-xl border border-slate-800 text-sm font-medium">
            <button
              onClick={() => setActiveTab('triage')}
              className={`flex items-center space-x-2 px-4 py-2 rounded-lg transition ${
                activeTab === 'triage'
                  ? 'bg-sky-600 text-white shadow'
                  : 'text-slate-400 hover:text-white hover:bg-slate-800/60'
              }`}
            >
              <ShieldAlert className="w-4 h-4" />
              <span>Flagged Triage Queue</span>
              {flaggedReport.length > 0 && (
                <span className="ml-1.5 px-2 py-0.2 bg-red-500 text-white rounded-full text-xs font-bold">
                  {flaggedReport.length}
                </span>
              )}
            </button>

            <button
              onClick={() => setActiveTab('ledger')}
              className={`flex items-center space-x-2 px-4 py-2 rounded-lg transition ${
                activeTab === 'ledger'
                  ? 'bg-sky-600 text-white shadow'
                  : 'text-slate-400 hover:text-white hover:bg-slate-800/60'
              }`}
            >
              <Activity className="w-4 h-4" />
              <span>Live Transaction Feed</span>
            </button>

            <button
              onClick={() => setActiveTab('account')}
              className={`flex items-center space-x-2 px-4 py-2 rounded-lg transition ${
                activeTab === 'account'
                  ? 'bg-sky-600 text-white shadow'
                  : 'text-slate-400 hover:text-white hover:bg-slate-800/60'
              }`}
            >
              <CreditCard className="w-4 h-4" />
              <span>Account Inspector</span>
            </button>

            <button
              onClick={() => setActiveTab('architecture')}
              className={`flex items-center space-x-2 px-4 py-2 rounded-lg transition ${
                activeTab === 'architecture'
                  ? 'bg-sky-600 text-white shadow'
                  : 'text-slate-400 hover:text-white hover:bg-slate-800/60'
              }`}
            >
              <Layers className="w-4 h-4" />
              <span>Architecture &amp; Rationale</span>
            </button>
          </nav>

          {/* Quick Submit Form for Demo */}
          <form onSubmit={handleQuickSubmit} className="flex items-center space-x-2 bg-slate-900 p-1.5 rounded-xl border border-slate-800 text-xs">
            <span className="text-slate-400 pl-2 font-mono">Test Tx:</span>
            <input
              type="number"
              placeholder="Acc ID"
              value={quickAccId}
              onChange={(e) => setQuickAccId(e.target.value)}
              className="w-14 bg-slate-950 border border-slate-700 px-2 py-1 rounded text-white"
            />
            <input
              type="number"
              placeholder="Amount"
              value={quickAmount}
              onChange={(e) => setQuickAmount(e.target.value)}
              className="w-20 bg-slate-950 border border-slate-700 px-2 py-1 rounded text-white"
            />
            <select
              value={quickType}
              onChange={(e) => setQuickType(e.target.value)}
              className="bg-slate-950 border border-slate-700 px-2 py-1 rounded text-white"
            >
              <option value="DEBIT">DEBIT</option>
              <option value="CREDIT">CREDIT</option>
            </select>
            <button
              type="submit"
              disabled={isSubmitting}
              className="bg-sky-600 hover:bg-sky-500 text-white px-3 py-1 rounded font-medium disabled:opacity-50"
            >
              {isSubmitting ? '...' : 'Submit'}
            </button>
          </form>
        </div>

        {/* TAB 1: TRIAGE QUEUE */}
        {activeTab === 'triage' && (
          <div className="space-y-4">
            <div className="flex items-center justify-between">
              <div>
                <h2 className="text-lg font-semibold text-white">Unreviewed Flagged Queue</h2>
                <p className="text-xs text-slate-400">
                  Prioritized by risk score and account partition using PostgreSQL <code className="text-sky-400">RANK() OVER (PARTITION BY account_id ORDER BY risk_score DESC)</code>.
                </p>
              </div>
              <button
                onClick={fetchData}
                className="flex items-center space-x-1 text-xs text-slate-400 hover:text-slate-200 bg-slate-900 border border-slate-800 px-3 py-1.5 rounded-lg"
              >
                <RefreshCw className="w-3.5 h-3.5" />
                <span>Refresh</span>
              </button>
            </div>

            {flaggedReport.length === 0 ? (
              <div className="bg-slate-900/50 border border-slate-800 rounded-xl p-12 text-center text-slate-400 space-y-2">
                <CheckCircle2 className="w-8 h-8 text-emerald-400 mx-auto" />
                <p className="font-medium text-slate-200">No Unreviewed Flags Pending</p>
                <p className="text-xs">All suspicious transactions have been triaged by analysts or no high-risk transactions detected.</p>
              </div>
            ) : (
              <div className="overflow-x-auto border border-slate-800 rounded-xl bg-slate-900/40">
                <table className="w-full text-left text-xs">
                  <thead className="bg-slate-900/80 text-slate-400 border-b border-slate-800 uppercase tracking-wider font-mono">
                    <tr>
                      <th className="py-3 px-4">Account Holder</th>
                      <th className="py-3 px-4">Tx ID</th>
                      <th className="py-3 px-4">Amount</th>
                      <th className="py-3 px-4">Risk Score</th>
                      <th className="py-3 px-4">Risk Rank</th>
                      <th className="py-3 px-4">Trigger Reason</th>
                      <th className="py-3 px-4 text-right">Analyst Actions</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-800 text-slate-300">
                    {flaggedReport.map((flag) => (
                      <tr key={flag.flagId || flag.transactionId} className="hover:bg-slate-800/30">
                        <td className="py-3.5 px-4 font-medium text-white">{flag.accountHolder}</td>
                        <td className="py-3.5 px-4 font-mono text-sky-400">#{flag.transactionId}</td>
                        <td className="py-3.5 px-4 font-bold text-white">${Number(flag.amount).toFixed(2)}</td>
                        <td className="py-3.5 px-4">
                          <span className={`px-2 py-0.5 rounded font-bold font-mono ${
                            flag.riskScore >= 0.85 ? 'bg-red-950 text-red-400 border border-red-800' : 'bg-amber-950 text-amber-400 border border-amber-800'
                          }`}>
                            {(flag.riskScore * 100).toFixed(0)}%
                          </span>
                        </td>
                        <td className="py-3.5 px-4 font-mono">Rank #{flag.riskRank}</td>
                        <td className="py-3.5 px-4 text-slate-400 max-w-xs truncate">{flag.reason}</td>
                        <td className="py-3.5 px-4 text-right space-x-2">
                          <button
                            onClick={() => handleReview(flag.flagId, 'APPROVED')}
                            className="bg-emerald-600/20 hover:bg-emerald-600 border border-emerald-500/40 text-emerald-300 hover:text-white px-3 py-1 rounded font-medium transition"
                          >
                            Approve
                          </button>
                          <button
                            onClick={() => handleReview(flag.flagId, 'BLOCKED')}
                            className="bg-red-600/20 hover:bg-red-600 border border-red-500/40 text-red-300 hover:text-white px-3 py-1 rounded font-medium transition"
                          >
                            Block
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        )}

        {/* TAB 2: LIVE TRANSACTION FEED */}
        {activeTab === 'ledger' && (
          <div className="space-y-4">
            <div className="flex items-center justify-between">
              <div>
                <h2 className="text-lg font-semibold text-white">Live Transaction Ledger</h2>
                <p className="text-xs text-slate-400">Continuous feed of transactions processed through atomic ledger boundaries.</p>
              </div>
            </div>

            <div className="overflow-x-auto border border-slate-800 rounded-xl bg-slate-900/40">
              <table className="w-full text-left text-xs">
                <thead className="bg-slate-900/80 text-slate-400 border-b border-slate-800 uppercase tracking-wider font-mono">
                  <tr>
                    <th className="py-3 px-4">Tx ID</th>
                    <th className="py-3 px-4">Idempotency Key</th>
                    <th className="py-3 px-4">Account</th>
                    <th className="py-3 px-4">Type</th>
                    <th className="py-3 px-4">Amount</th>
                    <th className="py-3 px-4">Status</th>
                    <th className="py-3 px-4">Fraud Check</th>
                    <th className="py-3 px-4">Created At</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-800 text-slate-300">
                  {transactions.map((tx) => (
                    <tr key={tx.transactionId} className="hover:bg-slate-800/30">
                      <td className="py-3 px-4 font-mono text-sky-400">#{tx.transactionId}</td>
                      <td className="py-3 px-4 font-mono text-slate-500 truncate max-w-[120px]">{tx.idempotencyKey}</td>
                      <td className="py-3 px-4 font-mono">Acc #{tx.accountId}</td>
                      <td className="py-3 px-4">
                        <span className={`px-2 py-0.5 rounded font-mono text-[10px] ${
                          tx.transactionType === 'DEBIT' ? 'bg-amber-950 text-amber-400' : 'bg-emerald-950 text-emerald-400'
                        }`}>
                          {tx.transactionType}
                        </span>
                      </td>
                      <td className="py-3 px-4 font-bold text-white">${Number(tx.amount).toFixed(2)}</td>
                      <td className="py-3 px-4">
                        <span className={`px-2 py-0.5 rounded font-bold text-[10px] ${
                          tx.status === 'COMPLETED' ? 'bg-emerald-950 text-emerald-400 border border-emerald-800' :
                          tx.status === 'FLAGGED' ? 'bg-red-950 text-red-400 border border-red-800' :
                          tx.status === 'REJECTED' ? 'bg-slate-800 text-slate-400 border border-slate-700' :
                          'bg-amber-950 text-amber-400'
                        }`}>
                          {tx.status}
                        </span>
                      </td>
                      <td className="py-3 px-4 font-mono">
                        <span className={tx.fraudCheckStatus === 'UNAVAILABLE' ? 'text-amber-400' : 'text-slate-400'}>
                          {tx.fraudCheckStatus || 'OK'}
                        </span>
                      </td>
                      <td className="py-3 px-4 text-slate-500 font-mono">{new Date(tx.createdAt).toLocaleTimeString()}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}

        {/* TAB 3: ACCOUNT INSPECTOR */}
        {activeTab === 'account' && (
          <div className="space-y-6">
            <div className="flex items-center space-x-3">
              <Search className="w-5 h-5 text-slate-400" />
              <input
                type="number"
                value={selectedAccountId}
                onChange={(e) => setSelectedAccountId(e.target.value)}
                placeholder="Enter Account ID (e.g. 1, 2, 3, 4)"
                className="bg-slate-900 border border-slate-800 px-4 py-2 rounded-lg text-white font-mono text-sm w-72"
              />
              <button
                onClick={() => fetchAccountDetails(selectedAccountId)}
                className="bg-sky-600 hover:bg-sky-500 text-white px-4 py-2 rounded-lg text-sm font-medium"
              >
                Inspect
              </button>
            </div>

            {accountData && (
              <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                <div className="bg-slate-900 border border-slate-800 rounded-xl p-5 space-y-1">
                  <p className="text-xs text-slate-400">Account Holder</p>
                  <p className="text-lg font-bold text-white">{accountData.accountHolder}</p>
                  <p className="text-xs text-slate-500 font-mono">ID: #{accountData.accountId}</p>
                </div>
                <div className="bg-slate-900 border border-slate-800 rounded-xl p-5 space-y-1">
                  <p className="text-xs text-slate-400">Current Balance</p>
                  <p className="text-2xl font-bold text-emerald-400 font-mono">${Number(accountData.balance).toFixed(2)}</p>
                  <p className="text-xs text-slate-500">Atomic Postgres Source of Truth</p>
                </div>
                <div className="bg-slate-900 border border-slate-800 rounded-xl p-5 space-y-1">
                  <p className="text-xs text-slate-400">Audit History Logged</p>
                  <p className="text-2xl font-bold text-sky-400 font-mono">{accountHistory.length} mutations</p>
                  <p className="text-xs text-slate-500">Row-level balance history rows</p>
                </div>
              </div>
            )}

            {/* Historical Balance Mutations */}
            <div className="space-y-3">
              <h3 className="text-sm font-semibold text-slate-200">Balance Mutation Audit Trail (balance_history table)</h3>
              <div className="overflow-x-auto border border-slate-800 rounded-xl bg-slate-900/40">
                <table className="w-full text-left text-xs">
                  <thead className="bg-slate-900/80 text-slate-400 border-b border-slate-800 uppercase tracking-wider font-mono">
                    <tr>
                      <th className="py-2.5 px-4">Audit ID</th>
                      <th className="py-2.5 px-4">Tx ID</th>
                      <th className="py-2.5 px-4">Balance Before</th>
                      <th className="py-2.5 px-4">Balance After</th>
                      <th className="py-2.5 px-4">Delta</th>
                      <th className="py-2.5 px-4">Timestamp</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-800 text-slate-300 font-mono">
                    {accountHistory.map((h) => {
                      const delta = Number(h.balanceAfter) - Number(h.balanceBefore);
                      return (
                        <tr key={h.id} className="hover:bg-slate-800/30">
                          <td className="py-2.5 px-4 text-slate-500">#{h.id}</td>
                          <td className="py-2.5 px-4 text-sky-400">#{h.transactionId}</td>
                          <td className="py-2.5 px-4">${Number(h.balanceBefore).toFixed(2)}</td>
                          <td className="py-2.5 px-4 font-bold text-white">${Number(h.balanceAfter).toFixed(2)}</td>
                          <td className={`py-2.5 px-4 font-bold ${delta >= 0 ? 'text-emerald-400' : 'text-amber-400'}`}>
                            {delta >= 0 ? `+$${delta.toFixed(2)}` : `-$${Math.abs(delta).toFixed(2)}`}
                          </td>
                          <td className="py-2.5 px-4 text-slate-500">{new Date(h.createdAt).toLocaleTimeString()}</td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        )}

        {/* TAB 4: ARCHITECTURE & RATIONALE */}
        {activeTab === 'architecture' && (
          <div className="space-y-6">
            <div className="bg-slate-900 border border-slate-800 rounded-xl p-6 space-y-4">
              <h3 className="text-base font-bold text-white flex items-center space-x-2">
                <Info className="w-5 h-5 text-sky-400" />
                <span>User-Centered Design &amp; Architecture Rationale</span>
              </h3>
              <p className="text-sm text-slate-300 leading-relaxed">
                The <strong>LedgerFlow Operations Dashboard</strong> is engineered around a <em>triage-first workflow</em> for risk analysts facing high transaction volumes. Rather than forcing analysts to parse a deluge of raw events, the interface immediately surfaces high-risk items ranked via PostgreSQL window functions (<code className="text-sky-400">RANK() OVER (PARTITION BY account_id ORDER BY risk_score DESC)</code>). In alignment with strict distributed systems principles, the UI contains zero financial or business logic: every Approve or Block action delegates to the authoritative Ledger Service API.
              </p>
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4 pt-2">
                <div className="bg-slate-950 p-4 rounded-lg border border-slate-800">
                  <h4 className="text-xs font-bold text-sky-400 uppercase tracking-wider mb-2 font-mono">1. Advisory vs. Authoritative Isolation</h4>
                  <p className="text-xs text-slate-400 leading-relaxed">
                    Fraud scoring is purely advisory (fail-open). If the Python fraud service is offline, the transaction still executes safely with an auditable <code className="text-amber-300">fraud_check_status = 'UNAVAILABLE'</code> flag. Conversely, Ledger availability is authoritative (fail-closed with idempotent retries).
                  </p>
                </div>
                <div className="bg-slate-950 p-4 rounded-lg border border-slate-800">
                  <h4 className="text-xs font-bold text-sky-400 uppercase tracking-wider mb-2 font-mono">2. Concurrency &amp; Row-Level Locking</h4>
                  <p className="text-xs text-slate-400 leading-relaxed">
                    Under concurrent balance updates, LedgerFlow guarantees zero balance corruption via PostgreSQL <code className="text-emerald-300">SELECT ... FOR UPDATE</code> row-level locking, completely eliminating the lost-update race condition that corrupts balances in un-synchronized systems.
                  </p>
                </div>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
