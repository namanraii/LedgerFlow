package com.ledgerflow.ingestion.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgerflow.ingestion.dto.IngestionRequest;
import com.ledgerflow.ingestion.dto.IngestionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class LedgerClient {
    private static final Logger log = LoggerFactory.getLogger(LedgerClient.class);

    private final String ledgerServiceUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    private static final int MAX_RETRIES = 3;
    private static final long[] BACKOFF_MS = {100, 400, 1600};

    public LedgerClient(@Value("${ledger.service.url:http://127.0.0.1:8081}") String ledgerServiceUrl,
                        ObjectMapper objectMapper) {
        this.ledgerServiceUrl = ledgerServiceUrl;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(1500))
                .build();
    }

    public IngestionResult submitTransactionWithRetry(IngestionRequest req, BigDecimal riskScore, String fraudStatus, String flagReason) {
        UUID idempotencyKey = req.getIdempotencyKey();
        String url = String.format("%s/api/v1/transactions", ledgerServiceUrl);
        long startTime = System.currentTimeMillis();

        Map<String, Object> body = new HashMap<>();
        body.put("idempotencyKey", idempotencyKey.toString());
        body.put("accountId", req.getAccountId());
        body.put("amount", req.getAmount());
        body.put("transactionType", req.getTransactionType());
        body.put("safe", req.isSafe());
        if (riskScore != null) {
            body.put("riskScore", riskScore);
        }
        if (flagReason != null && !flagReason.isBlank()) {
            body.put("flagReason", flagReason);
        }
        body.put("fraudCheckStatus", fraudStatus);

        IngestionResult result = new IngestionResult();
        result.setIdempotencyKey(idempotencyKey);
        result.setAccountId(req.getAccountId());
        result.setAmount(req.getAmount());
        result.setRiskScore(riskScore);
        result.setFraudCheckStatus(fraudStatus);

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            result.setRetryAttempts(attempt);
            try {
                String jsonBody = objectMapper.writeValueAsString(body);
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofMillis(2000))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();

                if (status == 200 || status == 201) {
                    var json = objectMapper.readTree(response.body());
                    result.setTransactionId(json.path("transactionId").asLong());
                    result.setStatus(json.path("status").asText("COMPLETED"));
                    result.setLatencyMs(System.currentTimeMillis() - startTime);
                    return result;
                } else if (status == 422) {
                    result.setStatus("REJECTED");
                    result.setErrorMessage("INSUFFICIENT_FUNDS");
                    result.setLatencyMs(System.currentTimeMillis() - startTime);
                    return result;
                } else if (status >= 400 && status < 500) {
                    result.setStatus("REJECTED");
                    result.setErrorMessage("CLIENT_ERROR_" + status + ": " + response.body());
                    result.setLatencyMs(System.currentTimeMillis() - startTime);
                    return result;
                } else {
                    log.warn("Ledger service returned HTTP {} on attempt {}/{}", status, attempt, MAX_RETRIES);
                }
            } catch (Exception e) {
                log.warn("Ledger request failed on attempt {}/{} ({}): {}", attempt, MAX_RETRIES, e.getClass().getSimpleName(), e.getMessage());
            }

            if (attempt < MAX_RETRIES) {
                try {
                    Thread.sleep(BACKOFF_MS[attempt - 1]);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        result.setStatus("FAILED_RETRYABLE");
        result.setErrorMessage("Ledger service unreachable after " + MAX_RETRIES + " retries");
        result.setLatencyMs(System.currentTimeMillis() - startTime);
        log.error("Transaction idempotency_key={} failed permanently as FAILED_RETRYABLE", idempotencyKey);
        return result;
    }
}
