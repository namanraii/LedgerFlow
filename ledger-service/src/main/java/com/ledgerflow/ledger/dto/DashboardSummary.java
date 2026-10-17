package com.ledgerflow.ledger.dto;

import java.math.BigDecimal;

public class DashboardSummary {
    private BigDecimal assetsUnderManagement = BigDecimal.ZERO;
    private long accountCount;
    private long transactionCount;
    private long completedCount;
    private long flaggedCount;
    private long rejectedCount;
    private long unreviewedFlags;
    private BigDecimal debitVolume = BigDecimal.ZERO;
    private BigDecimal creditVolume = BigDecimal.ZERO;
    private BigDecimal averageRisk = BigDecimal.ZERO;

    public BigDecimal getAssetsUnderManagement() { return assetsUnderManagement; }
    public void setAssetsUnderManagement(BigDecimal assetsUnderManagement) { this.assetsUnderManagement = assetsUnderManagement; }

    public long getAccountCount() { return accountCount; }
    public void setAccountCount(long accountCount) { this.accountCount = accountCount; }

    public long getTransactionCount() { return transactionCount; }
    public void setTransactionCount(long transactionCount) { this.transactionCount = transactionCount; }

    public long getCompletedCount() { return completedCount; }
    public void setCompletedCount(long completedCount) { this.completedCount = completedCount; }

    public long getFlaggedCount() { return flaggedCount; }
    public void setFlaggedCount(long flaggedCount) { this.flaggedCount = flaggedCount; }

    public long getRejectedCount() { return rejectedCount; }
    public void setRejectedCount(long rejectedCount) { this.rejectedCount = rejectedCount; }

    public long getUnreviewedFlags() { return unreviewedFlags; }
    public void setUnreviewedFlags(long unreviewedFlags) { this.unreviewedFlags = unreviewedFlags; }

    public BigDecimal getDebitVolume() { return debitVolume; }
    public void setDebitVolume(BigDecimal debitVolume) { this.debitVolume = debitVolume; }

    public BigDecimal getCreditVolume() { return creditVolume; }
    public void setCreditVolume(BigDecimal creditVolume) { this.creditVolume = creditVolume; }

    public BigDecimal getAverageRisk() { return averageRisk; }
    public void setAverageRisk(BigDecimal averageRisk) { this.averageRisk = averageRisk; }
}
