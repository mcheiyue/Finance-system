package com.gcc_0119.finance_tracker.repository;

import com.gcc_0119.finance_tracker.model.Transaction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataMongoTest
class TransactionRepositoryTest {

    @Autowired
    private TransactionRepository transactionRepository;

    @AfterEach
    void tearDown() {
        transactionRepository.deleteAll();
    }

    @Test
    void saveTransaction_withDoubleEntryFields_persistsCorrectly() {
        Transaction t = new Transaction();
        t.setUserId("user123");
        t.setAmount(new BigDecimal("100.50"));
        t.setFromAccountId("acct-from");
        t.setToAccountId("acct-to");
        t.setDescription("测试交易");
        t.setTimestamp(LocalDateTime.now());

        Transaction saved = transactionRepository.save(t);

        assertNotNull(saved.getId());
        assertEquals("user123", saved.getUserId());
        assertEquals(0, new BigDecimal("100.50").compareTo(saved.getAmount()));
        assertEquals("acct-from", saved.getFromAccountId());
        assertEquals("acct-to", saved.getToAccountId());
        assertEquals("测试交易", saved.getDescription());
        assertFalse(saved.isReversed());
        assertNull(saved.getReversalOfId());
        assertNotNull(saved.getCreatedAt());
    }

    @Test
    void findByUserId_returnsOnlyMatchingUser() {
        Transaction t1 = new Transaction();
        t1.setUserId("userA");
        t1.setAmount(BigDecimal.TEN);
        t1.setFromAccountId("from1");
        t1.setToAccountId("to1");
        t1.setTimestamp(LocalDateTime.now());

        Transaction t2 = new Transaction();
        t2.setUserId("userB");
        t2.setAmount(BigDecimal.ONE);
        t2.setFromAccountId("from2");
        t2.setToAccountId("to2");
        t2.setTimestamp(LocalDateTime.now());

        transactionRepository.save(t1);
        transactionRepository.save(t2);

        List<Transaction> userATxns = transactionRepository.findByUserId("userA");
        assertEquals(1, userATxns.size());
        assertEquals("from1", userATxns.get(0).getFromAccountId());
    }

    @Test
    void findByFromAccountId_returnsMatchingTransactions() {
        Transaction t = new Transaction();
        t.setUserId("user1");
        t.setAmount(new BigDecimal("50.00"));
        t.setFromAccountId("source-acct");
        t.setToAccountId("dest-acct");
        t.setTimestamp(LocalDateTime.now());
        transactionRepository.save(t);

        List<Transaction> found = transactionRepository.findByFromAccountId("source-acct");
        assertEquals(1, found.size());
        assertEquals("dest-acct", found.get(0).getToAccountId());
    }

    @Test
    void findByToAccountId_returnsMatchingTransactions() {
        Transaction t = new Transaction();
        t.setUserId("user1");
        t.setAmount(new BigDecimal("75.00"));
        t.setFromAccountId("acct-a");
        t.setToAccountId("acct-b");
        t.setTimestamp(LocalDateTime.now());
        transactionRepository.save(t);

        List<Transaction> found = transactionRepository.findByToAccountId("acct-b");
        assertEquals(1, found.size());
        assertEquals("acct-a", found.get(0).getFromAccountId());
    }

    @Test
    void defaultReversedIsFalse_createdAtIsNotNull() {
        Transaction t = new Transaction();
        t.setUserId("user1");
        t.setAmount(BigDecimal.TEN);
        t.setFromAccountId("from");
        t.setToAccountId("to");
        t.setTimestamp(LocalDateTime.now());

        Transaction saved = transactionRepository.save(t);

        assertFalse(saved.isReversed());
        assertNull(saved.getReversalOfId());
        assertNotNull(saved.getCreatedAt());
    }

    @Test
    void findByReversalOfId_returnsReversalTransaction() {
        Transaction original = new Transaction();
        original.setUserId("user1");
        original.setAmount(new BigDecimal("200.00"));
        original.setFromAccountId("acct-a");
        original.setToAccountId("acct-b");
        original.setTimestamp(LocalDateTime.now());
        original.setReversed(true);
        Transaction savedOriginal = transactionRepository.save(original);

        Transaction reversal = new Transaction();
        reversal.setUserId("user1");
        reversal.setAmount(new BigDecimal("200.00"));
        reversal.setFromAccountId("acct-b");
        reversal.setToAccountId("acct-a");
        reversal.setReversalOfId(savedOriginal.getId());
        reversal.setTimestamp(LocalDateTime.now());
        transactionRepository.save(reversal);

        List<Transaction> found = transactionRepository.findByReversalOfId(savedOriginal.getId());
        assertEquals(1, found.size());
        assertEquals("acct-b", found.get(0).getFromAccountId());
        assertEquals("acct-a", found.get(0).getToAccountId());
    }
}
