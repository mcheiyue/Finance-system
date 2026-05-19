package com.gcc_0119.finance_tracker.service;

import com.gcc_0119.finance_tracker.dto.AnomalyResult;
import com.gcc_0119.finance_tracker.model.Transaction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnomalyDetectionServiceTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @InjectMocks
    private AnomalyDetectionService anomalyDetectionService;

    private static final String USER_ID = "user1";
    private static final String ACCOUNT_ID = "acc-1";

    private Transaction buildTransaction(BigDecimal amount) {
        Transaction t = new Transaction();
        t.setUserId(USER_ID);
        t.setFromAccountId(ACCOUNT_ID);
        t.setAmount(amount);
        t.setReversed(false);
        t.setTimestamp(LocalDateTime.now().minusDays(1));
        return t;
    }

    @Test
    void detectAnomaly_normalAmount_returnsNotAnomalous() {
        List<Transaction> transactions = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            transactions.add(buildTransaction(new BigDecimal("100.00")));
        }

        when(mongoTemplate.find(any(Query.class), eq(Transaction.class)))
                .thenReturn(transactions);

        AnomalyResult result = anomalyDetectionService.detectAnomaly(
                USER_ID, ACCOUNT_ID, new BigDecimal("200.00"));

        assertFalse(result.isAnomalous());
        assertNull(result.getWarnings());
    }

    @Test
    void detectAnomaly_highAmount_returnsAnomalous() {
        List<Transaction> transactions = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            transactions.add(buildTransaction(new BigDecimal("100.00")));
        }

        when(mongoTemplate.find(any(Query.class), eq(Transaction.class)))
                .thenReturn(transactions);

        AnomalyResult result = anomalyDetectionService.detectAnomaly(
                USER_ID, ACCOUNT_ID, new BigDecimal("500.00"));

        assertTrue(result.isAnomalous());
        assertNotNull(result.getWarnings());
        assertFalse(result.getWarnings().isEmpty());
        assertEquals(0, new BigDecimal("100.00").compareTo(result.getAverageAmount()));
        assertEquals(0, new BigDecimal("500.00").compareTo(result.getCurrentAmount()));
    }

    @Test
    void detectAnomaly_insufficientData_returnsNormal() {
        List<Transaction> transactions = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            transactions.add(buildTransaction(new BigDecimal("100.00")));
        }

        when(mongoTemplate.find(any(Query.class), eq(Transaction.class)))
                .thenReturn(transactions);

        AnomalyResult result = anomalyDetectionService.detectAnomaly(
                USER_ID, ACCOUNT_ID, new BigDecimal("10000.00"));

        assertFalse(result.isAnomalous());
    }

    @Test
    void detectAnomaly_exactThreshold_returnsNotAnomalous() {
        List<Transaction> transactions = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            transactions.add(buildTransaction(new BigDecimal("100.00")));
        }

        when(mongoTemplate.find(any(Query.class), eq(Transaction.class)))
                .thenReturn(transactions);

        BigDecimal threshold = new BigDecimal("100.00").multiply(AnomalyDetectionService.ANOMALY_MULTIPLIER);
        AnomalyResult result = anomalyDetectionService.detectAnomaly(
                USER_ID, ACCOUNT_ID, threshold);

        assertFalse(result.isAnomalous());
    }

    @Test
    void detectAnomaly_emptyTransactionHistory_returnsNormal() {
        when(mongoTemplate.find(any(Query.class), eq(Transaction.class)))
                .thenReturn(Collections.emptyList());

        AnomalyResult result = anomalyDetectionService.detectAnomaly(
                USER_ID, ACCOUNT_ID, new BigDecimal("5000.00"));

        assertFalse(result.isAnomalous());
    }

    @Test
    void detectAnomaly_justAboveThreshold_returnsAnomalous() {
        List<Transaction> transactions = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            transactions.add(buildTransaction(new BigDecimal("100.00")));
        }

        when(mongoTemplate.find(any(Query.class), eq(Transaction.class)))
                .thenReturn(transactions);

        BigDecimal justAboveThreshold = new BigDecimal("100.00")
                .multiply(AnomalyDetectionService.ANOMALY_MULTIPLIER)
                .add(new BigDecimal("0.01"));

        AnomalyResult result = anomalyDetectionService.detectAnomaly(
                USER_ID, ACCOUNT_ID, justAboveThreshold);

        assertTrue(result.isAnomalous());
        assertNotNull(result.getWarnings());
        assertEquals(1, result.getWarnings().size());
    }
}
