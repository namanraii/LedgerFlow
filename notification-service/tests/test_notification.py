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

@pytest.mark.parametrize("payload", [None, [1, 2], "alert", 42])
def test_alert_buffer_rejects_non_object_payloads(payload):
    buffer = AlertBuffer(maxlen=5)
    seen = []
    buffer.subscribe(seen.append)

    assert buffer.add(payload) is False
    assert buffer.get_all() == []
    assert seen == []

def test_alert_buffer_does_not_mutate_caller_dict():
    buffer = AlertBuffer(maxlen=5)
    original = {"account_id": 7, "risk_score": 0.8}

    assert buffer.add(original) is True
    assert original == {"account_id": 7, "risk_score": 0.8}
    stored = buffer.get_all()[0]
    assert stored["account_id"] == 7
    assert "received_at" in stored

def test_alert_buffer_failing_listener_does_not_block_others():
    buffer = AlertBuffer(maxlen=5)
    seen = []

    def broken(alert):
        raise RuntimeError("client gone")

    buffer.subscribe(broken)
    buffer.subscribe(seen.append)
    buffer.add({"account_id": 3})

    assert [a["account_id"] for a in seen] == [3]
    assert len(buffer.get_all()) == 1
