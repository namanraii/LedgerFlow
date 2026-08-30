package com.ledgerflow.ingestion.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgerflow.ingestion.dto.FraudEvaluationResponse;
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
import java.util.Map;
import java.util.Optional;

@Component
public class FraudClient {
    private static final Logger log = LoggerFactory.getLogger(FraudClient.class);

    private final String fraudServiceUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public FraudClient(@Value("${fraud.service.url:http://localhost:8000}") String fraudServiceUrl,
                       ObjectMapper objectMapper) {
        this.fraudServiceUrl = fraudServiceUrl;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(800))
                .build();
    }

    public Optional<FraudEvaluationResponse> evaluateRisk(Long accountId, BigDecimal amount, String transactionType) {
        String url = String.format("%s/api/v1/evaluate", fraudServiceUrl);
        try {
            Map<String, Object> payload = Map.of(
                    "account_id", accountId,
                    "amount", amount.doubleValue(),
                    "transaction_type", transactionType
            );
            String jsonPayload = objectMapper.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(1000))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                var jsonNode = objectMapper.readTree(response.body());
                BigDecimal riskScore = new BigDecimal(jsonNode.path("risk_score").asText("0.0"));
                boolean flagged = jsonNode.path("flagged").asBoolean(false);
                String reason = jsonNode.path("reason").asText("");
                return Optional.of(new FraudEvaluationResponse(riskScore, flagged, reason));
            } else {
                log.warn("Fraud service returned HTTP {}, degrading gracefully", response.statusCode());
                return Optional.empty();
            }
        } catch (Exception e) {
            log.warn("Fraud service call failed ({}), proceeding in degraded mode (risk_score=NULL, status=UNAVAILABLE)", e.getMessage());
            return Optional.empty();
        }
    }
}
