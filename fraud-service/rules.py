import time
import threading
from collections import defaultdict
from typing import Dict, List, Tuple

class VelocityTracker:
    """
    Thread-safe in-memory rolling window tracker for account transaction velocity.
    """
    def __init__(self, window_seconds: float = 60.0, max_allowed: int = 5):
        self.window_seconds = window_seconds
        self.max_allowed = max_allowed
        self._history: Dict[int, List[float]] = defaultdict(list)
        self._lock = threading.Lock()

    def record_and_check(self, account_id: int) -> Tuple[int, float]:
        """
        Records an event and returns (recent_count, velocity_risk_component).
        """
        now = time.time()
        with self._lock:
            timestamps = self._history[account_id]
            cutoff = now - self.window_seconds
            valid_timestamps = [t for t in timestamps if t > cutoff]
            valid_timestamps.append(now)
            self._history[account_id] = valid_timestamps
            count = len(valid_timestamps)

        if count > self.max_allowed:
            risk = min(1.0, 0.5 + (count - self.max_allowed) * 0.20)
        elif count >= self.max_allowed - 1:
            risk = 0.30
        else:
            risk = 0.05
        return count, risk

    def clear(self):
        with self._lock:
            self._history.clear()

def evaluate_amount_anomaly(amount: float) -> Tuple[float, str]:
    """
    Rule 1: Amount Anomaly.
    """
    if amount > 25000.0:
        return 0.90, "Large transaction amount > $25,000"
    elif amount > 10000.0:
        return 0.60, "Elevated transaction amount > $10,000"
    elif amount > 5000.0:
        return 0.30, "Moderate transaction amount > $5,000"
    return 0.05, "Normal transaction amount"

def evaluate_round_number_heuristic(amount: float) -> Tuple[float, str]:
    """
    Rule 3: Round Number Heuristic.
    """
    if amount >= 2500.0 and amount % 1000.0 == 0.0:
        return 0.40, f"High-value round amount: ${amount:.2f}"
    return 0.0, ""

def compute_risk_score(account_id: int, amount: float, tracker: VelocityTracker) -> Tuple[float, bool, str]:
    """
    Computes composite weighted risk score (0.0 - 1.0).
    Threshold is 0.70.
    """
    amount_risk, amount_reason = evaluate_amount_anomaly(amount)
    round_risk, round_reason = evaluate_round_number_heuristic(amount)
    velocity_count, velocity_risk = tracker.record_and_check(account_id)

    composite = (0.55 * amount_risk) + (0.30 * velocity_risk) + (0.15 * round_risk)
    composite = round(min(1.0, composite), 4)

    reasons = []
    if amount_risk >= 0.50:
        reasons.append(amount_reason)
    if velocity_count > tracker.max_allowed:
        reasons.append(f"High velocity: {velocity_count} txs in 60s")
    if round_risk > 0:
        reasons.append(round_reason)

    if not reasons:
        reasons.append("Standard transaction risk profile")

    flagged = composite >= 0.70
    return composite, flagged, "; ".join(reasons)
