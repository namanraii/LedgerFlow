import logging
import os
from fastapi import FastAPI, status
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field
from rules import VelocityTracker, compute_risk_score
from messaging import publish_fraud_alert

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(name)s: %(message)s")
logger = logging.getLogger("fraud-service")

app = FastAPI(title="LedgerFlow Fraud Detection Service", version="1.0.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

velocity_tracker = VelocityTracker(window_seconds=60.0, max_allowed=5)

class EvaluationRequest(BaseModel):
    account_id: int = Field(..., description="Account ID")
    amount: float = Field(..., gt=0, description="Transaction amount")
    transaction_type: str = Field(default="DEBIT", description="Transaction type")

class EvaluationResponse(BaseModel):
    account_id: int
    amount: float
    risk_score: float
    flagged: bool
    reason: str
    alert_published: bool

@app.post("/api/v1/evaluate", response_model=EvaluationResponse)
def evaluate_transaction(req: EvaluationRequest):
    risk_score, flagged, reason = compute_risk_score(req.account_id, req.amount, velocity_tracker)
    
    alert_published = False
    if flagged:
        alert_payload = {
            "account_id": req.account_id,
            "amount": req.amount,
            "transaction_type": req.transaction_type,
            "risk_score": risk_score,
            "reason": reason
        }
        alert_published = publish_fraud_alert(alert_payload)

    logger.info(f"Evaluated account={req.account_id} amount={req.amount} score={risk_score} flagged={flagged}")
    return EvaluationResponse(
        account_id=req.account_id,
        amount=req.amount,
        risk_score=risk_score,
        flagged=flagged,
        reason=reason,
        alert_published=alert_published
    )

@app.get("/api/v1/health")
def health_check():
    return {"status": "UP", "service": "fraud-service", "version": "1.0.0"}

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
