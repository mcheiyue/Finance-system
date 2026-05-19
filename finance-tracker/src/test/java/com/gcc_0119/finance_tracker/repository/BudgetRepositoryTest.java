package com.gcc_0119.finance_tracker.repository;

import com.gcc_0119.finance_tracker.model.Budget;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexOperations;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataMongoTest
class BudgetRepositoryTest {

    @Autowired
    private BudgetRepository budgetRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @BeforeEach
    void setUp() {
        // Ensure compound index is created (not auto-created by default in tests)
        IndexOperations indexOps = mongoTemplate.indexOps(Budget.class);
        indexOps.ensureIndex(
            new org.springframework.data.mongodb.core.index.CompoundIndexDefinition(
                new org.bson.Document("userId", 1).append("accountId", 1)
            ).unique().named("userId_accountId_unique")
        );
    }

    @AfterEach
    void tearDown() {
        budgetRepository.deleteAll();
    }

    @Test
    void createAndQueryBudget() {
        Budget budget = new Budget();
        budget.setUserId("user123");
        budget.setAccountId("account456");
        budget.setLimitAmount(new BigDecimal("5000.00"));

        Budget saved = budgetRepository.save(budget);

        assertNotNull(saved.getId());
        assertNotNull(saved.getCreatedAt());
        assertEquals("user123", saved.getUserId());
        assertEquals("account456", saved.getAccountId());
        assertEquals(0, new BigDecimal("5000.00").compareTo(saved.getLimitAmount()));
    }

    @Test
    void uniqueIndex_duplicateUserIdAndAccountId_shouldFail() {
        Budget budget1 = new Budget();
        budget1.setUserId("user123");
        budget1.setAccountId("account456");
        budget1.setLimitAmount(new BigDecimal("5000.00"));
        budgetRepository.save(budget1);

        Budget budget2 = new Budget();
        budget2.setUserId("user123");
        budget2.setAccountId("account456");
        budget2.setLimitAmount(new BigDecimal("8000.00"));

        assertThrows(DuplicateKeyException.class, () -> budgetRepository.save(budget2));
    }

    @Test
    void findByUserId_returnsList() {
        Budget budget1 = new Budget();
        budget1.setUserId("user123");
        budget1.setAccountId("account1");
        budget1.setLimitAmount(new BigDecimal("3000.00"));

        Budget budget2 = new Budget();
        budget2.setUserId("user123");
        budget2.setAccountId("account2");
        budget2.setLimitAmount(new BigDecimal("7000.00"));

        Budget budget3 = new Budget();
        budget3.setUserId("user456");
        budget3.setAccountId("account3");
        budget3.setLimitAmount(new BigDecimal("10000.00"));

        budgetRepository.save(budget1);
        budgetRepository.save(budget2);
        budgetRepository.save(budget3);

        List<Budget> user123Budgets = budgetRepository.findByUserId("user123");
        assertEquals(2, user123Budgets.size());

        List<Budget> user456Budgets = budgetRepository.findByUserId("user456");
        assertEquals(1, user456Budgets.size());
    }

    @Test
    void findByUserIdAndAccountId_returnsCorrectBudget() {
        Budget budget = new Budget();
        budget.setUserId("user123");
        budget.setAccountId("account456");
        budget.setLimitAmount(new BigDecimal("5000.00"));
        budgetRepository.save(budget);

        Optional<Budget> found = budgetRepository.findByUserIdAndAccountId("user123", "account456");
        assertTrue(found.isPresent());
        assertEquals(0, new BigDecimal("5000.00").compareTo(found.get().getLimitAmount()));

        Optional<Budget> notFound = budgetRepository.findByUserIdAndAccountId("user123", "nonexistent");
        assertFalse(notFound.isPresent());
    }

    @Test
    void existsByUserIdAndAccountId() {
        Budget budget = new Budget();
        budget.setUserId("user123");
        budget.setAccountId("account456");
        budget.setLimitAmount(new BigDecimal("5000.00"));
        budgetRepository.save(budget);

        assertTrue(budgetRepository.existsByUserIdAndAccountId("user123", "account456"));
        assertFalse(budgetRepository.existsByUserIdAndAccountId("user123", "nonexistent"));
        assertFalse(budgetRepository.existsByUserIdAndAccountId("nonexistent", "account456"));
    }
}
