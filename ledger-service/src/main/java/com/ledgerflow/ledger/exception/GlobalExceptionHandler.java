package com.ledgerflow.ledger.exception;

import com.ledgerflow.ledger.dto.ApiErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleAccountNotFound(AccountNotFoundException ex) {
        log.warn("Account not found: {}", ex.getAccountId());
        ApiErrorResponse err = new ApiErrorResponse(
                "ACCOUNT_NOT_FOUND",
                ex.getMessage(),
                null,
                false
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(err);
    }

    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ApiErrorResponse> handleInsufficientFunds(InsufficientFundsException ex) {
        log.warn("Insufficient funds on account {}: current balance {}, requested {}",
                ex.getAccountId(), ex.getCurrentBalance(), ex.getRequestedAmount());
        ApiErrorResponse err = new ApiErrorResponse(
                "INSUFFICIENT_FUNDS",
                ex.getMessage(),
                ex.getTransactionId(),
                false // 4xx business error is non-retryable
        );
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(err); // 422
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationErrors(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Validation failed");
        log.warn("Validation error: {}", message);
        ApiErrorResponse err = new ApiErrorResponse(
                "INVALID_REQUEST",
                message,
                null,
                false
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(err); // 400
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Illegal argument: {}", ex.getMessage());
        ApiErrorResponse err = new ApiErrorResponse(
                "INVALID_REQUEST",
                ex.getMessage(),
                null,
                false
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(err); // 400
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGeneralException(Exception ex) {
        log.error("Unexpected internal error: ", ex);
        ApiErrorResponse err = new ApiErrorResponse(
                "INTERNAL_ERROR",
                "An unexpected server error occurred: " + ex.getMessage(),
                null,
                true // 500 is retryable
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(err); // 500
    }
}
