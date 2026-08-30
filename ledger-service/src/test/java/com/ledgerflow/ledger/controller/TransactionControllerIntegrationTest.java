package com.ledgerflow.ledger.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgerflow.ledger.domain.Account;
import com.ledgerflow.ledger.dto.TransactionRequest;
import com.ledgerflow.ledger.repository.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class TransactionControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccountRepository accountRepository;

    private Long accountId;

    @BeforeEach
    void setUp() {
        Account acc = accountRepository.save(new Account("API Test User", new BigDecimal("500.00")));
        accountId = acc.getAccountId();
    }

    @Test
    void testValidTransactionReturns201() throws Exception {
        TransactionRequest req = new TransactionRequest();
        req.setIdempotencyKey(UUID.randomUUID());
        req.setAccountId(accountId);
        req.setAmount(new BigDecimal("50.00"));
        req.setTransactionType("DEBIT");

        mockMvc.perform(post("/api/v1/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.balanceAfter").value(450.00));
    }

    @Test
    void testInsufficientFundsReturns422() throws Exception {
        TransactionRequest req = new TransactionRequest();
        req.setIdempotencyKey(UUID.randomUUID());
        req.setAccountId(accountId);
        req.setAmount(new BigDecimal("9999.00"));
        req.setTransactionType("DEBIT");

        mockMvc.perform(post("/api/v1/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("INSUFFICIENT_FUNDS"))
                .andExpect(jsonPath("$.retryable").value(false));
    }

    @Test
    void testInvalidPayloadReturns400() throws Exception {
        TransactionRequest req = new TransactionRequest();
        // Missing idempotency key and negative amount
        req.setAccountId(accountId);
        req.setAmount(new BigDecimal("-10.00"));
        req.setTransactionType("INVALID_TYPE");

        mockMvc.perform(post("/api/v1/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"));
    }

    @Test
    void testAccountNotFoundReturns404() throws Exception {
        TransactionRequest req = new TransactionRequest();
        req.setIdempotencyKey(UUID.randomUUID());
        req.setAccountId(999999L);
        req.setAmount(new BigDecimal("50.00"));
        req.setTransactionType("DEBIT");

        mockMvc.perform(post("/api/v1/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("ACCOUNT_NOT_FOUND"));
    }
}
