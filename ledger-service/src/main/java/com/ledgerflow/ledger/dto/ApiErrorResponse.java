package com.ledgerflow.ledger.dto;

import java.time.LocalDateTime;

public class ApiErrorResponse {
    private String error;
    private String message;
    private Long transactionId;
    private boolean retryable;
    private LocalDateTime timestamp = LocalDateTime.now();

    public ApiErrorResponse() {}

    public ApiErrorResponse(String error, String message, Long transactionId, boolean retryable) {
        this.error = error;
        this.message = message;
        this.transactionId = transactionId;
        this.retryable = retryable;
        this.timestamp = LocalDateTime.now();
    }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public Long getTransactionId() { return transactionId; }
    public void setTransactionId(Long transactionId) { this.transactionId = transactionId; }

    public boolean isRetryable() { return retryable; }
    public void setRetryable(boolean retryable) { this.retryable = retryable; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}
