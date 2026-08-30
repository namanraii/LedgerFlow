import pytest
from rules import VelocityTracker, evaluate_amount_anomaly, evaluate_round_number_heuristic, compute_risk_score

def test_amount_anomaly_normal():
    score, reason = evaluate_amount_anomaly(100.0)
    assert score == 0.05

def test_amount_anomaly_extreme():
    score, reason = evaluate_amount_anomaly(30000.0)
    assert score >= 0.80
    assert "Large transaction" in reason

def test_round_number_heuristic():
    score, reason = evaluate_round_number_heuristic(5000.0)
    assert score == 0.40
    
    score_normal, _ = evaluate_round_number_heuristic(5000.50)
    assert score_normal == 0.0

def test_velocity_tracker():
    tracker = VelocityTracker(window_seconds=10.0, max_allowed=3)
    account_id = 999
    
    c1, r1 = tracker.record_and_check(account_id)
    assert c1 == 1
    assert r1 == 0.05

    c2, r2 = tracker.record_and_check(account_id)
    c3, r3 = tracker.record_and_check(account_id)
    assert c3 == 3
    assert r3 == 0.30

    c4, r4 = tracker.record_and_check(account_id)
    assert c4 == 4
    assert r4 >= 0.50

def test_composite_risk_score_flagged():
    tracker = VelocityTracker(window_seconds=10.0, max_allowed=2)
    account_id = 101
    
    # Large transaction + trigger velocity
    tracker.record_and_check(account_id)
    tracker.record_and_check(account_id)
    
    score, flagged, reason = compute_risk_score(account_id, 30000.0, tracker)
    assert score >= 0.70
    assert flagged is True
