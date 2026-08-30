import pytest
from consumer import AlertBuffer

def test_alert_buffer_add_and_get():
    buffer = AlertBuffer(maxlen=5)
    
    buffer.add({"account_id": 1, "risk_score": 0.85, "reason": "High amount"})
    buffer.add({"account_id": 2, "risk_score": 0.90, "reason": "High velocity"})
    
    alerts = buffer.get_all()
    assert len(alerts) == 2
    assert alerts[0]["account_id"] == 2 # Most recent first
    assert "received_at" in alerts[0]

def test_alert_buffer_max_capacity():
    buffer = AlertBuffer(maxlen=3)
    for i in range(5):
        buffer.add({"account_id": i})
        
    alerts = buffer.get_all()
    assert len(alerts) == 3
    assert alerts[0]["account_id"] == 4
