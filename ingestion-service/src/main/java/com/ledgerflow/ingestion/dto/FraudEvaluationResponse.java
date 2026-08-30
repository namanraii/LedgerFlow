package com.ledgerflow.ingestion.dto;

import java.math.BigDecimal;

public class FraudEvaluationResponse {
    private BigDecimal riskScore;
    private boolean flagged;
    private String reason;

    public FraudEvaluationResponse() {}
    public FraudEvaluationResponse(BigDecimal riskScore, boolean flagged, String reason) {
        this.riskScore = riskScore;
        this.flagged = flagged;
        this.reason = reason;
    }

    public BigDecimal getRiskScore() { return riskScore; }
    public void setRiskScore(BigDecimal riskScore) { this.riskScore = riskScore; }

    public boolean isFlagged() { return flagged; }
    public void setFlagged(boolean flagged) { this.flagged = flagged; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
